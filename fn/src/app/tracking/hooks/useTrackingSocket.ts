import { useEffect, useRef, useState } from "react";
import { Client } from "@stomp/stompjs";
import { useAuth } from "@/lib/auth/AuthContext";
import type { VehicleLiveSnapshot } from "../api/tracking.types";

function buildStompUrl(): string {
  return (import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8087/api/v1")
    .replace(/\/api\/v1\/?$/, "")
    .replace(/^http/, "ws")
    .concat("/ws/tracking");
}

export interface TrackingSocketState {
  vehicles: Map<string, VehicleLiveSnapshot>;
  connected: boolean;
}

/**
 * Subscribes to /topic/tracking/route/{id} for every routeId supplied.
 * Re-connects (and re-subscribes) whenever the sorted route set changes.
 */
export function useTrackingSocket(routeIds: string[]): TrackingSocketState {
  const [vehicles, setVehicles] = useState<Map<string, VehicleLiveSnapshot>>(new Map());
  const [connected, setConnected] = useState(false);
  const { user } = useAuth();
  const token = user?.access_token;

  // Stable key — only reconnect when the actual route set changes
  const routeKey = [...routeIds].sort().join(",");

  // Keep a ref so onConnect closure always sees current routeIds
  const routeIdsRef = useRef(routeIds);
  useEffect(() => { routeIdsRef.current = routeIds; });

  useEffect(() => {
    if (!routeKey) return;

    const push = (msg: { body: string }) => {
      try {
        const snap = JSON.parse(msg.body) as VehicleLiveSnapshot;
        setVehicles((prev) => new Map(prev).set(snap.busId, snap));
      } catch { /* skip malformed frame */ }
    };

    const client = new Client({
      brokerURL: buildStompUrl(),
      connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
      reconnectDelay: 5_000,
      onConnect: () => {
        setConnected(true);
        // Receive initial snapshots pushed back per-subscribe request
        client.subscribe("/user/queue/tracking", push);
        for (const id of routeIdsRef.current) {
          client.subscribe(`/topic/tracking/route/${id}`, push);
          client.publish({
            destination: "/app/tracking/subscribe",
            body: JSON.stringify({ routeId: id }),
          });
        }
      },
      onDisconnect: () => setConnected(false),
      onStompError:  () => setConnected(false),
    });

    client.activate();
    return () => { client.deactivate(); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [routeKey, token]);

  return { vehicles, connected };
}
