export default function TradeHistory({ trades }) {
  return (
    <div className="panel trade-tape">
      <h2>Live Trade Tape</h2>
      {trades.length === 0 && <p style={{ color: "#7d8598", fontSize: 13 }}>No trades yet.</p>}
      {trades.map((t, i) => (
        <div className="trade-row buy" key={t.id || i}>
          <span>{Number(t.price).toFixed(2)}</span>
          <span>{t.quantity}</span>
          <span style={{ color: "#7d8598" }}>{new Date(t.createdAt).toLocaleTimeString()}</span>
        </div>
      ))}
    </div>
  );
}
