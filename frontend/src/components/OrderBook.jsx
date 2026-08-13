export default function OrderBook({ orderbook }) {
  const { bids = [], asks = [] } = orderbook;
  return (
    <div className="panel">
      <h2>Order Book</h2>
      <div className="asks">
        {[...asks].reverse().map((a, i) => (
          <div className="book-row" key={`ask-${i}`}>
            <span>{a.price.toFixed(2)}</span>
            <span>{a.quantity}</span>
          </div>
        ))}
      </div>
      <div style={{ borderTop: "1px solid #232838", borderBottom: "1px solid #232838", margin: "6px 0" }} />
      <div className="bids">
        {bids.map((b, i) => (
          <div className="book-row" key={`bid-${i}`}>
            <span>{b.price.toFixed(2)}</span>
            <span>{b.quantity}</span>
          </div>
        ))}
      </div>
      {bids.length === 0 && asks.length === 0 && (
        <p style={{ color: "#7d8598", fontSize: 13 }}>No open orders yet. Place one below.</p>
      )}
    </div>
  );
}
