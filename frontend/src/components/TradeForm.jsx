import { useState } from "react";

export default function TradeForm({ apiBase, accountId, symbol, onOrderPlaced, onError }) {
  const [side, setSide] = useState("buy");
  const [type, setType] = useState("limit");
  const [price, setPrice] = useState("");
  const [quantity, setQuantity] = useState("");
  const [submitting, setSubmitting] = useState(false);

  async function submit(e) {
    e.preventDefault();
    if (!accountId) return onError("Create/select an account first.");
    setSubmitting(true);
    try {
      const res = await fetch(`${apiBase}/orders`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Idempotency-Key": crypto.randomUUID(),
        },
        body: JSON.stringify({
          accountId,
          symbol,
          side,
          type,
          price: type === "limit" ? Number(price) : undefined,
          quantity: Number(quantity),
        }),
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || "Order failed");
      onOrderPlaced(data);
      setQuantity("");
    } catch (err) {
      onError(err.message);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form className="order-form panel" onSubmit={submit}>
      <h2>Place Order — {symbol}</h2>
      <div className="side-toggle">
        <button type="button" className={side === "buy" ? "active-buy" : ""} onClick={() => setSide("buy")}>
          Buy
        </button>
        <button type="button" className={side === "sell" ? "active-sell" : ""} onClick={() => setSide("sell")}>
          Sell
        </button>
      </div>

      <label>Order Type</label>
      <select value={type} onChange={(e) => setType(e.target.value)}>
        <option value="limit">Limit</option>
        <option value="market">Market</option>
      </select>

      {type === "limit" && (
        <>
          <label>Price (USD)</label>
          <input type="number" min="0" step="0.01" value={price} onChange={(e) => setPrice(e.target.value)} required />
        </>
      )}

      <label>Quantity (BTC)</label>
      <input type="number" min="0" step="0.0001" value={quantity} onChange={(e) => setQuantity(e.target.value)} required />

      <button className="submit-btn" type="submit" disabled={submitting}>
        {submitting ? "Placing..." : `${side === "buy" ? "Buy" : "Sell"} ${symbol.split("-")[0]}`}
      </button>
    </form>
  );
}
