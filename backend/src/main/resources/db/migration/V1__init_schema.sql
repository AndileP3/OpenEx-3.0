-- OpenEx 3.0 core schema
-- Day 2 of the plan: accounts, orders, trades, ledger_entries (double-entry),
-- with DB-level constraints enforcing no negative balances and referential
-- integrity between orders/trades/ledger and accounts.

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE accounts (
    id          UUID PRIMARY KEY,
    name        TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
    
);

-- One row per (account, asset). This is the fast-read cache of balance;
-- ledger_entries below is the append-only source of truth / audit trail.
CREATE TABLE balances (
    id          UUID PRIMARY KEY,
    account_id  UUID NOT NULL REFERENCES accounts(id),
    asset       TEXT NOT NULL,
    amount      NUMERIC(24, 8) NOT NULL DEFAULT 0,
    CONSTRAINT balances_amount_non_negative CHECK (amount >= 0),
    CONSTRAINT balances_account_asset_unique UNIQUE (account_id, asset)
);

CREATE TABLE ledger_entries (
    id             UUID PRIMARY KEY,
    account_id     UUID NOT NULL REFERENCES accounts(id),
    asset          TEXT NOT NULL,
    amount         NUMERIC(24, 8) NOT NULL,        -- positive = credit, negative = debit
    balance_after  NUMERIC(24, 8) NOT NULL,
    type           TEXT NOT NULL CHECK (type IN ('DEPOSIT', 'WITHDRAW', 'TRADE')),
    ref_id         TEXT,
    group_id       UUID NOT NULL,                   -- links the legs of one atomic event
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ledger_entries_account_id ON ledger_entries(account_id);
CREATE INDEX idx_ledger_entries_group_id ON ledger_entries(group_id);

CREATE TABLE orders (
    id                UUID PRIMARY KEY,
    account_id        UUID NOT NULL REFERENCES accounts(id),
    symbol            TEXT NOT NULL,
    side              TEXT NOT NULL CHECK (side IN ('BUY', 'SELL')),
    type              TEXT NOT NULL CHECK (type IN ('LIMIT', 'MARKET')),
    price             NUMERIC(24, 8),
    quantity          NUMERIC(24, 8) NOT NULL CHECK (quantity > 0),
    remaining         NUMERIC(24, 8) NOT NULL CHECK (remaining >= 0),
    status            TEXT NOT NULL DEFAULT 'OPEN'
                        CHECK (status IN ('OPEN', 'PARTIAL', 'FILLED', 'CANCELLED', 'REJECTED')),
    idempotency_key   TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT orders_limit_requires_price CHECK (type = 'MARKET' OR price IS NOT NULL)
);
CREATE INDEX idx_orders_account_id ON orders(account_id);
CREATE INDEX idx_orders_book_lookup ON orders(symbol, side, status, price, created_at);
-- idempotency key must be unique when present, but many orders will have none
CREATE UNIQUE INDEX idx_orders_idempotency_key ON orders(idempotency_key) WHERE idempotency_key IS NOT NULL;

CREATE TABLE trades (
    id                  UUID PRIMARY KEY,
    symbol              TEXT NOT NULL,
    price               NUMERIC(24, 8) NOT NULL,
    quantity            NUMERIC(24, 8) NOT NULL CHECK (quantity > 0),
    buy_order_id        UUID NOT NULL REFERENCES orders(id),
    sell_order_id       UUID NOT NULL REFERENCES orders(id),
    buyer_account_id    UUID NOT NULL REFERENCES accounts(id),
    seller_account_id   UUID NOT NULL REFERENCES accounts(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_trades_symbol ON trades(symbol, created_at DESC);
