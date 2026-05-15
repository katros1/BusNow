import { useEffect, useState } from "react";
import { Client } from "@stomp/stompjs";
import { useAuth } from "@/lib/auth/AuthContext";
import type { VehicleLiveSnapshot } from "../api/tracking.types";

function buildStompUrl(): string {
  return (import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8087/api/v1")
    .replace(/\/api\/v1\/?$/, "")
    .replace(/^http/, "ws")
    .concat("/ws/tracking");
}

export interface VehicleSocketState {
  snapshot: VehicleLiveSnapshot | null;
  connected: boolean;
}

export function useVehicleSocket(
  plateNumber: string | undefined,
  routeId: string | null | undefined
): VehicleSocketState {
  const [snapshot, setSnapshot] = useState<VehicleLiveSnapshot | null>(null);
  const [connected, setConnected] = useState(false);
  const { user } = useAuth();
  const token = user?.access_token;

  useEffect(() => {
    if (!plateNumber) return;

    const client = new Client({
      brokerURL: buildStompUrl(),
      connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
      reconnectDelay: 5_000,
      onConnect: () => {
        setConnected(true);
        client.subscribe("/user/queue/tracking", (msg) => {
          try { setSnapshot(JSON.parse(msg.body)); } catch { /* skip */ }
        });
        client.subscribe(`/topic/tracking/vehicle/${plateNumber}`, (msg) => {
          try { setSnapshot(JSON.parse(msg.body)); } catch { /* skip */ }
        });
        client.publish({
          destination: "/app/tracking/subscribe",
          body: JSON.stringify({ plateNumber, routeId: routeId ?? undefined, continueTracking: true }),
        });
      },
      onDisconnect: () => setConnected(false),
      onStompError:  () => setConnected(false),
    });

    client.activate();
    return () => { client.deactivate(); };
  }, [plateNumber, routeId, token]);

  return { snapshot, connected };
}
