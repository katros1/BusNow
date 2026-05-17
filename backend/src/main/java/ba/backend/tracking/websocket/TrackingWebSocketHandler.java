package ba.backend.tracking.websocket;

import ba.backend.tracking.dto.VehicleLiveSnapshot;
import ba.backend.tracking.service.GpsIngestService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Raw WebSocket handler for the live tracking pipeline.
 *
 * Protocol (JSON text frames):
 *
 * Client → Server:
 *   { "type": "subscribe", "plates": ["ABC-123"] }
 *   { "type": "ping" }
 *
 * Server → Client:
 *   { "type": "connected" }
 *   { "type": "snapshot", "data": { ...VehicleLiveSnapshot } }
 *   { "type": "pong" }
 *
 * Called from two thread contexts:
 *   - Spring WS thread pool  → afterConnectionEstablished / handleTextMessage / afterConnectionClosed
 *   - Redis listener thread  → pushToPlate
 * WebSocketSession.sendMessage is NOT thread-safe; pushToPlate synchronises on each session.
 */
@Component
public class TrackingWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(TrackingWebSocketHandler.class);

    private final TrackingSessionRegistry registry;
    private final GpsIngestService        ingestService;
    private final ObjectMapper            objectMapper;

    public TrackingWebSocketHandler(TrackingSessionRegistry registry,
            GpsIngestService ingestService, ObjectMapper objectMapper) {
        this.registry      = registry;
        this.ingestService = ingestService;
        this.objectMapper  = objectMapper;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        registry.register(session);
        send(session, Map.of("type", "connected"));
        log.debug("WS connected: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JsonNode node = objectMapper.readTree(message.getPayload());
            switch (node.path("type").asText("")) {
                case "subscribe" -> handleSubscribe(session, node);
                case "ping"      -> send(session, Map.of("type", "pong"));
                default          -> { /* ignore unknown */ }
            }
        } catch (Exception e) {
            log.debug("Bad WS message from {}: {}", session.getId(), e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        registry.unregister(session.getId());
        log.debug("WS closed: {} status={}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable ex) {
        registry.unregister(session.getId());
        log.warn("WS transport error {}: {}", session.getId(), ex.getMessage());
    }

    // ── Subscribe handler ─────────────────────────────────────────────────────

    private void handleSubscribe(WebSocketSession session, JsonNode node) {
        JsonNode platesNode = node.path("plates");
        if (!platesNode.isArray()) return;

        for (JsonNode p : platesNode) {
            String plate = p.asText("").trim();
            if (plate.isEmpty()) continue;
            registry.subscribe(session.getId(), plate);
            pushCurrentSnapshot(session, plate);
        }
    }

    private void pushCurrentSnapshot(WebSocketSession session, String plate) {
        ingestService.getBusStates().values().stream()
                .map(GpsIngestService.BusState::lastSnapshot)
                .filter(s -> s != null && plate.equalsIgnoreCase(s.plateNumber()))
                .findFirst()
                .ifPresent(snap -> sendSnapshot(session, snap));
    }

    // ── Push from Redis relay ─────────────────────────────────────────────────

    /**
     * Called by {@link RedisTrackingRelay} on the Redis listener thread.
     * Synchronized per-session to prevent concurrent writes on the same connection.
     */
    public void pushToPlate(String plate, VehicleLiveSnapshot snapshot) {
        TextMessage msg = buildSnapshotMessage(snapshot);
        if (msg == null) return;

        for (WebSocketSession session : registry.getSessionsForPlate(plate)) {
            synchronized (session) {
                try {
                    if (session.isOpen()) session.sendMessage(msg);
                } catch (IOException e) {
                    log.warn("Failed to push to session {}: {}", session.getId(), e.getMessage());
                    registry.unregister(session.getId());
                }
            }
        }
    }

    // ── Keepalive heartbeat ───────────────────────────────────────────────────

    /**
     * Sends a native WebSocket PING frame to every open session every 25 seconds.
     * Keeps the connection alive through reverse proxies that close idle connections.
     * The browser automatically replies with a PONG frame — no application code needed.
     */
    @Scheduled(fixedRate = 25_000)
    public void sendHeartbeat() {
        PingMessage ping = new PingMessage(ByteBuffer.wrap(new byte[0]));
        for (WebSocketSession session : registry.getAllSessions()) {
            synchronized (session) {
                try {
                    if (session.isOpen()) session.sendMessage(ping);
                } catch (IOException e) {
                    log.debug("Heartbeat failed for {}, removing", session.getId());
                    registry.unregister(session.getId());
                }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void sendSnapshot(WebSocketSession session, VehicleLiveSnapshot snap) {
        TextMessage msg = buildSnapshotMessage(snap);
        if (msg == null) return;
        synchronized (session) {
            try {
                if (session.isOpen()) session.sendMessage(msg);
            } catch (IOException e) {
                log.warn("Failed to send snapshot to {}: {}", session.getId(), e.getMessage());
            }
        }
    }

    private TextMessage buildSnapshotMessage(VehicleLiveSnapshot snap) {
        try {
            ObjectNode wrapper = objectMapper.createObjectNode();
            wrapper.put("type", "snapshot");
            wrapper.set("data", objectMapper.valueToTree(snap));
            return new TextMessage(objectMapper.writeValueAsString(wrapper));
        } catch (Exception e) {
            log.error("Failed to serialize snapshot: {}", e.getMessage());
            return null;
        }
    }

    private void send(WebSocketSession session, Object payload) {
        synchronized (session) {
            try {
                if (session.isOpen())
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
            } catch (IOException e) {
                log.warn("Failed to send to {}: {}", session.getId(), e.getMessage());
            }
        }
    }
}
