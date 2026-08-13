import { useEffect, useRef, useState } from "react";

/**
 * Connects to the backend WebSocket and keeps a live order book + trade tape
 * in state. Reconnects automatically with backoff if the connection drops.
 */
export function useLiveMarket(wsUrl, apiBase, symbol) {
  const [connected, setConnected] = useState(false);
  const [orderbook, setOrderbook] = useState({ bids: [], asks: [] });
  const [trades, setTrades] = useState([]);
  const socketRef = useRef(null);

  // Initial snapshot over REST - the WS connection only pushes updates from
  // this point forward, it doesn't replay history on connect.
  useEffect(() => {
    fetch(`${apiBase}/orderbook/${symbol}`)
      .then((r) => r.json())
      .then(setOrderbook)
      .catch(() => {});
    fetch(`${apiBase}/trades/${symbol}`)
      .then((r) => r.json())
      .then(setTrades)
      .catch(() => {});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    let retryDelay = 1000;
    let stopped = false;
    let socket;

    function connect() {
      socket = new WebSocket(wsUrl);
      socketRef.current = socket;

      socket.onopen = () => {
        setConnected(true);
        retryDelay = 1000;
      };

      socket.onmessage = (event) => {
        const msg = JSON.parse(event.data);
        if (msg.type === "orderbook") {
          setOrderbook(msg.data);
        } else if (msg.type === "trade") {
          setTrades((prev) => [msg.data, ...prev].slice(0, 30));
        }
      };

      socket.onclose = () => {
        setConnected(false);
        if (!stopped) {
          setTimeout(connect, retryDelay);
          retryDelay = Math.min(retryDelay * 2, 15000);
        }
      };

      socket.onerror = () => socket.close();
    }

    connect();
    return () => {
      stopped = true;
      socketRef.current?.close();
    };
  }, [wsUrl]);

  return { connected, orderbook, trades };
}
