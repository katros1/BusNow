import { useCallback, useEffect, useRef, useState } from "react";
import type { VehiclePositionEvent } from "../api/tracking.types";

function buildWsUrl(): string {
  const api =
    import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8087/api/v1";
  return api
    .replace(/\/api\/v1\/?$/, "")
    .replace(/^http/, "ws")
    .concat("/ws/live");
}

const DELAYS = [1_000, 2_000, 5_000, 10_000, 30_000];

export interface TrackingSocketState {
  vehicles: Map<string, VehiclePositionEvent>;
  connected: boolean;
}

export function useTrackingSocket(): TrackingSocketState {
  const [vehicles, setVehicles] = useState<Map<string, VehiclePositionEvent>>(
    new Map()
  );
  const [connected, setConnected] = useState(false);

  const wsRef   = useRef<WebSocket | null>(null);
  const retries = useRef(0);
  const alive   = useRef(true);
  const timer   = useRef<ReturnType<typeof setTimeout> | null>(null);

  const connect = useCallback(() => {
    if (!alive.current) return;
    try {
      const ws = new WebSocket(buildWsUrl());
      wsRef.current = ws;

      ws.onopen = () => {
        if (!alive.current) { ws.close(); return; }
        setConnected(true);
        retries.current = 0;
      };

      ws.onmessage = ({ data }) => {
        if (!alive.current) return;
        try {
          const evt = JSON.parse(data as string) as VehiclePositionEvent;
          setVehicles((prev) => new Map(prev).set(evt.busId, evt));
        } catch { /* skip malformed frame */ }
      };

      ws.onclose = () => {
        if (!alive.current) return;
        setConnected(false);
        const delay = DELAYS[Math.min(retries.current++, DELAYS.length - 1)];
        timer.current = setTimeout(connect, delay);
      };

      ws.onerror = () => ws.close();
    } catch { /* invalid URL */ }
  }, []);

  useEffect(() => {
    alive.current = true;
    connect();
    return () => {
      alive.current = false;
      if (timer.current) clearTimeout(timer.current);
      wsRef.current?.close();
    };
  }, [connect]);

  return { vehicles, connected };
}
