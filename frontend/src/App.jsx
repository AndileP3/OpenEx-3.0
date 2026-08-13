import { useEffect, useState } from "react";
import OrderBook from "./components/OrderBook.jsx";
import TradeForm from "./components/TradeForm.jsx";
import TradeHistory from "./components/TradeHistory.jsx";
import { useLiveMarket } from "./hooks/useWebSocket.js";

const API_BASE = import.meta.env.VITE_API_BASE || "http://localhost:8080/api";
const WS_URL = import.meta.env.VITE_WS_URL || "ws://localhost:8080/ws";
const SYMBOL = "BTC-USD";

export default function App() {
  const { connected, orderbook, trades } = useLiveMarket(WS_URL, API_BASE, SYMBOL);
  const [accountId, setAccountId] = useState(localStorage.getItem("openex_account_id") || "");
  const [accountName, setAccountName] = useState("");
  const [balances, setBalances] = useState({});
  const [error, setError] = useState("");

  useEffect(() => {
    if (accountId) refreshBalances();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [accountId]);

  async function refreshBalances() {
    try {
      const res = await fetch(`${API_BASE}/wallet/${accountId}`);
      if (res.ok) setBalances(await res.json());
    } catch {
      /* network hiccup, ignore */
    }
  }

  async function createAccount() {
    if (!accountName.trim()) return;
    const res = await fetch(`${API_BASE}/accounts`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ name: accountName }),
    });
    const data = await res.json();
    setAccountId(data.id);
    localStorage.setItem("openex_account_id", data.id);
  }

  async function deposit(asset, amount) {
    await fetch(`${API_BASE}/wallet/deposit`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ accountId, asset, amount }),
    });
    refreshBalances();
  }

  return (
    <div className="app">
      <div className="header">
        <h1>OpenEx 3.0 — Holonet</h1>
        <div>
          <span className={`status-dot ${connected ? "connected" : "disconnected"}`} />
          {connected ? "Live" : "Disconnected"}
        </div>
      </div>

      {error && <div className="error-banner">{error}</div>}

      <div className="account-bar">
        {!accountId ? (
          <>
            <input placeholder="Account name (e.g. Alice)" value={accountName} onChange={(e) => setAccountName(e.target.value)} />
            <button onClick={createAccount}>Create Account</button>
          </>
        ) : (
          <>
            <span style={{ color: "#7d8598" }}>Account: {accountId}</span>
            <button onClick={() => deposit("USD", 10000)}>+ $10,000 USD</button>
            <button onClick={() => deposit("BTC", 1)}>+ 1 BTC</button>
            <button
              onClick={() => {
                localStorage.removeItem("openex_account_id");
                setAccountId("");
                setBalances({});
              }}
            >
              Switch Account
            </button>
          </>
        )}
      </div>

      {accountId && (
        <div className="balances">
          {Object.entries(balances).map(([asset, amount]) => (
            <div className="balance-chip" key={asset}>
              {amount} {asset}
            </div>
          ))}
          {Object.keys(balances).length === 0 && <span style={{ color: "#7d8598", fontSize: 13 }}>No balances yet — deposit funds above.</span>}
        </div>
      )}

      <div className="grid" style={{ marginTop: 16 }}>
        <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
          <OrderBook orderbook={orderbook} />
          <TradeHistory trades={trades} />
        </div>
        <TradeForm
          apiBase={API_BASE}
          accountId={accountId}
          symbol={SYMBOL}
          onOrderPlaced={() => {
            setError("");
            refreshBalances();
          }}
          onError={setError}
        />
      </div>
    </div>
  );
}
