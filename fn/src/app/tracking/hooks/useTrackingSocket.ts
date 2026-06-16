import { useCallback, useEffect, useRef, useState } from "react";
import { useAuth } from "@/lib/auth/AuthContext";
import type { VehicleLiveSnapshot } from "../api/tracking.types";

function buildWsUrl(token: string | undefined): string {
  const base = (import.meta.env.VITE_API_BASE_URL || "http://localhost:8087/api/v1")
    .replace(/\/api\/v1\/?$/, "")
    .replace(/^http/, "ws");
  return token ? `${base}/ws/tracking?token=${encodeURIComponent(token)}` : `${base}/ws/tracking`;
}

const MAX_RETRY_DELAY_MS = 30_000;

export interface TrackingSocketState {
  vehicles: Map<string, VehicleLiveSnapshot>;
  connected: boolean;
}

/**
 * Native WebSocket hook that subscribes to live snapshots for every plate supplied.
 *
 * Protocol (JSON text frames):
 *   Client → Server: { type: "subscribe", plates: [...] }
 *   Server → Client: { type: "snapshot", data: VehicleLiveSnapshot }
 *                    { type: "pong" }
 *
 * Reconnects with exponential backoff (1 s → 2 s → 4 s … 30 s max).
 * Re-subscribes automatically after each reconnect.
 */
export function useTrackingSocket(plates: string[]): TrackingSocketState {
  const [vehicles, setVehicles]   = useState<Map<string, VehicleLiveSnapshot>>(new Map());
  const [connected, setConnected] = useState(false);
  const { user } = useAuth();
  const token = user?.access_token;

  const wsRef        = useRef<WebSocket | null>(null);
  const connectedRef = useRef(false);
  const retryTimer   = useRef<ReturnType<typeof setTimeout> | null>(null);
  const retryCount   = useRef(0);
  const mountedRef   = useRef(true);
  const platesRef    = useRef<string[]>(plates);
  const tokenRef     = useRef(token);

  useEffect(() => { platesRef.current = plates; });
  useEffect(() => { tokenRef.current = token; });

  const push = useCallback((snap: VehicleLiveSnapshot) => {
    setVehicles(prev => new Map(prev).set(snap.busId, snap));
  }, []);

  const doSubscribe = useCallback((ws: WebSocket) => {
    const current = platesRef.current;
    if (ws.readyState === WebSocket.OPEN && current.length > 0) {
      ws.send(JSON.stringify({ type: "subscribe", plates: current }));
    }
  }, []);

  const connect = useCallback(() => {
    if (!mountedRef.current) return;
    if (wsRef.current && wsRef.current.readyState < WebSocket.CLOSING) return;

    const ws = new WebSocket(buildWsUrl(tokenRef.current));
    wsRef.current = ws;

    ws.onopen = () => {
      if (!mountedRef.current) { ws.close(); return; }
      retryCount.current = 0;
      connectedRef.current = true;
      setConnected(true);
      doSubscribe(ws);
    };

    ws.onmessage = (evt: MessageEvent<string>) => {
      try {
        const msg = JSON.parse(evt.data) as { type: string; data?: VehicleLiveSnapshot };
        if (msg.type === "snapshot" && msg.data) push(msg.data);
      } catch { /* skip malformed frames */ }
    };

    ws.onclose = () => {
      connectedRef.current = false;
      setConnected(false);
      wsRef.current = null;
      if (!mountedRef.current) return;
      const delay = Math.min(1_000 * 2 ** retryCount.current, MAX_RETRY_DELAY_MS);
      retryCount.current = Math.min(retryCount.current + 1, 10);
      retryTimer.current = setTimeout(connect, delay);
    };

    ws.onerror = () => ws.close();
  }, []); // stable — reads latest values via refs

  // Connect once on mount; reconnect when token changes (new URL)
  useEffect(() => {
    mountedRef.current = true;
    retryCount.current = 0;
    connect();
    return () => {
      mountedRef.current = false;
      if (retryTimer.current) clearTimeout(retryTimer.current);
      wsRef.current?.close();
    };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  // When plates list changes after connection is already up, re-subscribe
  useEffect(() => {
    if (connectedRef.current && wsRef.current) {
      doSubscribe(wsRef.current);
    }
  }, [plates, doSubscribe]);

  return { vehicles, connected };
}
