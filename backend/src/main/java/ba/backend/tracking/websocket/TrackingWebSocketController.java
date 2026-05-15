package ba.backend.tracking.websocket;

import ba.backend.tracking.dto.TrackingSubscribeRequest;
import ba.backend.tracking.dto.VehicleLiveSnapshot;
import ba.backend.tracking.service.GpsIngestService;
import ba.backend.tracking.service.GpsIngestService.BusState;
import java.util.Map;
import java.util.UUID;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

/**
 * Handles the initial WebSocket subscription handshake.
 *
 * After the client sends its subscription request to {@code /app/tracking/subscribe},
 * this controller looks up the current live snapshots and pushes them immediately
 * to the client's personal queue, so the map is populated without waiting for the
 * next GPS tick.
 *
 * Subsequent real-time updates arrive via:
 *   /topic/tracking/route/{routeId}       – all buses on a route
 *   /topic/tracking/vehicle/{plateNumber} – one specific bus
 */
@Controller
public class TrackingWebSocketController {

    private final GpsIngestService      ingestService;
    private final SimpMessagingTemplate stomp;

    public TrackingWebSocketController(GpsIngestService ingestService, SimpMessagingTemplate stomp) {
        this.ingestService = ingestService;
        this.stomp         = stomp;
    }

    @MessageMapping("/tracking/subscribe")
    public void onSubscribe(TrackingSubscribeRequest request, SimpMessageHeaderAccessor headers) {
        String sessionId = headers.getSessionId();
        if (sessionId == null) return;

        Map<UUID, BusState> states = ingestService.getBusStates();

        if (request.plateNumber() != null && !request.plateNumber().isBlank()) {
            // Single-vehicle subscription: push current snapshot for that bus
            states.values().stream()
                    .filter(s -> s.lastSnapshot() != null
                            && request.plateNumber().equalsIgnoreCase(s.lastSnapshot().plateNumber()))
                    .map(BusState::lastSnapshot)
                    .findFirst()
                    .ifPresent(snap -> sendToSession(sessionId, snap));
        } else if (request.routeId() != null) {
            // Route subscription: push current snapshots for all buses on that route
            states.values().stream()
                    .map(BusState::lastSnapshot)
                    .filter(snap -> snap != null && request.routeId().equals(snap.routeId()))
                    .forEach(snap -> sendToSession(sessionId, snap));
        }
    }

    private void sendToSession(String sessionId, VehicleLiveSnapshot snapshot) {
        SimpMessageHeaderAccessor ha = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        ha.setSessionId(sessionId);
        ha.setLeaveMutable(true);
        stomp.convertAndSendToUser(sessionId, "/queue/tracking", snapshot, ha.getMessageHeaders());
    }
}
