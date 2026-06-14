import { useCallback, useEffect, useRef, useState } from "react";
import { useAuth } from "@/lib/auth/AuthContext";
import type { VehicleLiveSnapshot } from "../api/tracking.types";

function buildWsUrl(token: string | undefined): string {
  const base = (import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8087/api/v1")
    .replace(/\/api\/v1\/?$/, "")
    .replace(/^http/, "ws");
  return token ? `${base}/ws/tracking?token=${encodeURIComponent(token)}` : `${base}/ws/tracking`;
}

const MAX_RETRY_DELAY_MS = 30_000;

export interface VehicleSocketState {
  snapshot: VehicleLiveSnapshot | null;
  connected: boolean;
}

/**
 * Single-vehicle native WebSocket hook.
 *
 * Maintains one persistent connection per plate.
 * Reconnects with exponential backoff on close/error.
 * Sends initial subscribe on connect so the server pushes the current snapshot immediately.
 */
export function useVehicleSocket(plateNumber: string | undefined): VehicleSocketState {
  const [snapshot, setSnapshot]   = useState<VehicleLiveSnapshot | null>(null);
  const [connected, setConnected] = useState(false);
  const { user } = useAuth();
  const token = user?.access_token;

  const wsRef        = useRef<WebSocket | null>(null);
  const retryTimer   = useRef<ReturnType<typeof setTimeout> | null>(null);
  const retryCount   = useRef(0);
  const mountedRef   = useRef(true);
  const plateRef     = useRef(plateNumber);
  const tokenRef     = useRef(token);
  const connectRef   = useRef<(() => void) | null>(null);

  useEffect(() => { plateRef.current = plateNumber; });
  useEffect(() => { tokenRef.current = token; });

  const connect = useCallback(() => {
    if (!mountedRef.current || !plateRef.current) return;
    if (wsRef.current && wsRef.current.readyState < WebSocket.CLOSING) return;

    const ws = new WebSocket(buildWsUrl(tokenRef.current));
    wsRef.current = ws;

    ws.onopen = () => {
      if (!mountedRef.current) { ws.close(); return; }
      retryCount.current = 0;
      setConnected(true);
      if (plateRef.current) {
        ws.send(JSON.stringify({ type: "subscribe", plates: [plateRef.current] }));
      }
      // Keep the connection alive through proxies that close idle WebSockets.
      const pingInterval = setInterval(() => {
        if (ws.readyState === WebSocket.OPEN) {
          ws.send(JSON.stringify({ type: "ping" }));
        }
      }, 25_000);
      ws.addEventListener("close", () => clearInterval(pingInterval), { once: true });
    };

    ws.onmessage = (evt: MessageEvent<string>) => {
      try {
        const msg = JSON.parse(evt.data) as { type: string; data?: VehicleLiveSnapshot };
        if (msg.type === "snapshot" && msg.data) setSnapshot(msg.data);
      } catch { /* skip malformed frames */ }
    };

    ws.onclose = () => {
      setConnected(false);
      wsRef.current = null;
      if (!mountedRef.current) return;
      const delay = Math.min(1_000 * 2 ** retryCount.current, MAX_RETRY_DELAY_MS);
      retryCount.current = Math.min(retryCount.current + 1, 10);
      retryTimer.current = setTimeout(() => connectRef.current?.(), delay);
    };

    ws.onerror = () => ws.close();
  }, []); // stable — reads via refs

  useEffect(() => { connectRef.current = connect; }, [connect]);

  // Connect/reconnect when plate or token changes
  useEffect(() => {
    mountedRef.current = true;
    retryCount.current = 0;
    if (plateNumber) connect();
    return () => {
      mountedRef.current = false;
      if (retryTimer.current) clearTimeout(retryTimer.current);
      wsRef.current?.close();
    };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [plateNumber, token]);

  // Filter out stale snapshots from a previous plate
  return { snapshot: snapshot?.plateNumber === plateNumber ? snapshot : null, connected };
}
