# OpenEx 3.0 — Kotlin + Spring Boot + PostgreSQL

This build follows the original execution plan as written: **Kotlin + Spring Boot backend,
PostgreSQL with Flyway migrations, double-entry ledger, price-time-priority matching engine,
WebSocket-broadcast order book, React frontend, and a Python AI microservice stub for Week 3.**

## Important — please read before you build

I could not compile or run this Kotlin/Maven project myself. My sandbox can only reach
npm/PyPI/GitHub, not Maven Central, so `mvn compile` isn't possible where I built this. What I
*did* do:
- Wrote every file by hand following standard, well-established Spring Boot + Kotlin + JPA +
  Flyway conventions (the same patterns Spring Initializr generates).
- Ran an automated brace/parenthesis balance check across every `.kt` file — all clean.
- Built and ran the React frontend for real (`npm install && npm run build` succeeded) against
  the new API shape.

What I could **not** do: actually resolve Maven dependencies, compile the Kotlin sources, or hit
the API with a live PostgreSQL instance. **The first time you run `mvn spring-boot:run`, treat it
as the real first compile** — if something doesn't build, send me the error and I'll fix it
immediately; Kotlin/Spring stack traces are very precise about what's wrong and where.

The Week 3 AI microservice is a working stub, same as before: real FastAPI scaffolding and
backend integration, but the actual LangChain+Ollama call is a marked TODO since it needs a
locally running LLM I don't have access to.

## Prerequisites

Install these before you start:
- **JDK 17+** — `java -version`
- **Maven 3.9+** — `mvn -version` ([install guide](https://maven.apache.org/install.html))
- **Docker + Docker Compose** — for PostgreSQL
- **Node.js 18+** — for the frontend
- **Python 3.10+** — only if you want to run the optional AI service stub

## Project structure

```
openex/
├── backend/            # Kotlin + Spring Boot + PostgreSQL (Maven project)
├── frontend/            # React + Vite live trading dashboard
├── ai-service/           # Python FastAPI stub (Week 3)
└── docker-compose.yml    # PostgreSQL
```

---

## Step 1 — Start PostgreSQL

```bash
cd openex
docker compose up -d
```

This starts `postgres:16` on `localhost:5432` with:
- database: `openex`
- user: `openex`
- password: `openex`

Check it's healthy:
```bash
docker compose ps
# openex-postgres should show "healthy"
```

You can inspect the DB directly at any point with:
```bash
docker exec -it openex-postgres psql -U openex -d openex
```

## Step 2 — Build and run the backend

```bash
cd openex/backend
mvn clean install
```

This will download all dependencies (Spring Boot, Kotlin compiler plugins, PostgreSQL driver,
Flyway) from Maven Central — the first run can take a few minutes.

On startup, **Flyway automatically applies the schema migration**
(`src/main/resources/db/migration/V1__init_schema.sql`), which creates:
- `accounts`
- `balances` — one row per (account, asset), with a `CHECK (amount >= 0)` constraint, so a
  negative balance is physically impossible at the database level, not just app-level
- `ledger_entries` — the append-only double-entry audit trail
- `orders` — with a partial unique index on `idempotency_key` so repeated order submissions with
  the same key never create duplicates
- `trades`

Run it with the `dev` profile so it seeds two demo accounts (Alice, Bob) with starting balances:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Watch the startup logs for two lines like:
```
[seed] Alice account id: 3f2b1a...
[seed] Bob account id:   9c8d7e...
```
Copy Alice's ID — you'll paste it into the frontend to log in as her.

Health check: `curl http://localhost:8080/health`

To run without seeding (e.g. a second time, since seeding is skipped once accounts exist
anyway): `mvn spring-boot:run`

### Configuration

All config is in `src/main/resources/application.yml`, overridable via environment variables:

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/openex` | Postgres connection string |
| `DB_USER` | `openex` | DB user |
| `DB_PASSWORD` | `openex` | DB password |
| `SERVER_PORT` | `8080` | backend HTTP port |
| `SYMBOL` | `BTC-USD` | trading pair |
| `SEED_DATA` | `false` | seed demo accounts (also set by the `dev` profile) |

### Key REST endpoints

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/accounts` | create account `{name}` |
| GET | `/api/wallet/:accountId` | get balances |
| POST | `/api/wallet/deposit` | `{accountId, asset, amount}` |
| POST | `/api/wallet/withdraw` | `{accountId, asset, amount}` |
| POST | `/api/orders` | place order `{accountId, symbol, side, type, price?, quantity}` — supports `Idempotency-Key` header |
| DELETE | `/api/orders/:id` | cancel an open order |
| GET | `/api/orderbook/:symbol` | current book snapshot |
| GET | `/api/trades/:symbol` | recent trades |
| WS | `ws://localhost:8080/ws` | live `orderbook` and `trade` push messages |

## Step 3 — Run the frontend

```bash
cd openex/frontend
cp .env.example .env       # defaults already point at localhost:8080
npm install
npm run dev                 # http://localhost:5173
```

Open http://localhost:5173, create an account (or open devtools → Application → Local Storage
and set `openex_account_id` to Alice's seeded UUID to trade as her directly), deposit funds with
the quick-deposit buttons, and place buy/sell orders.

Open the app in two tabs — one as Alice, one as Bob — place a sell order in one and a crossing
buy order in the other, and watch the trade execute and the order book update live over
WebSocket in both tabs.

## Step 4 — (Optional) Run the AI service stub

```bash
cd openex/ai-service
python3 -m venv venv && source venv/bin/activate
pip install -r requirements.txt
uvicorn app:app --reload --port 8000
```
```bash
curl -X POST http://localhost:8000/ask \
  -H "Content-Type: application/json" \
  -d '{"accountId": "<Alice UUID>", "question": "what is my balance?"}'
```
To wire in a real LLM: install Ollama, pull a model (`ollama pull llama3`), uncomment the
langchain lines in `requirements.txt`, and replace the marked block in `app.py` with a real
LangChain agent call.

---

## Architecture notes

- **Double-entry ledger, DB-enforced**: every balance change writes matching debit/credit
  `LedgerEntry` rows sharing a `group_id`. The `balances` table additionally has a
  `CHECK (amount >= 0)` constraint as a hard backstop — even a bug in application logic can't
  push a balance negative.
- **Concurrency safety**: balance mutations use `SELECT ... FOR UPDATE` (`@Lock(PESSIMISTIC_WRITE)`)
  on the specific `(account_id, asset)` row being touched. A trade settlement (4 legs: buyer
  debit/credit, seller debit/credit) locks all four rows up front in a globally consistent sort
  order, so two concurrent trades touching overlapping accounts can never deadlock each other.
- **Matching engine**: in-memory order book per symbol (`ConcurrentHashMap<String, SymbolBook>`),
  rebuilt from open/partial DB orders via an `ApplicationRunner` on boot. Each symbol's book has
  its own lock, so matching is strictly sequential per symbol — required for correct price-time
  priority — while different symbols can match concurrently.
- **Idempotency**: `POST /api/orders` accepts an `Idempotency-Key` header; a partial unique index
  on `orders.idempotency_key` plus an app-level check means a retried request returns the
  original order instead of creating a duplicate.
- **Money as BigDecimal**: all prices/quantities/balances use `NUMERIC(24,8)` in Postgres and
  `BigDecimal` in Kotlin — never floating point — so there's no rounding drift in ledger math.

## What I'd still do before calling this production-ready

- Auth (no login/JWT yet — anyone with an account UUID can trade as that account)
- Rate limiting on order placement
- Idempotency-Key uniqueness race: two simultaneous requests with the same brand-new key could
  both slip past the `findByIdempotencyKey` check before either commits — worth adding a
  catch on the DB unique-constraint violation to handle that edge case explicitly
- Multi-instance scaling would need the in-memory order book moved to something shared (e.g.
  rebuilding matching as a single dedicated service, or Redis-backed book state)
- Real integration tests run against a live PostgreSQL (I could only structurally verify this
  build — please run through Steps 1-3 once locally end-to-end, and let me know what breaks)
