package ba.backend.tracking.websocket;

import ba.backend.tracking.TrackingChannels;
import ba.backend.tracking.dto.VehicleLiveSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/**
 * Bridges Redis pub/sub → raw WebSocket sessions.
 *
 * Listens on {@code tracking:*} and for each vehicle-channel message
 * pushes the snapshot to all WebSocket sessions subscribed to that plate.
 *
 * Route-level channels (tracking:route:*) are intentionally ignored —
 * the frontend subscribes per-plate for stability across direction toggles.
 */
@Component
public class RedisTrackingRelay implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisTrackingRelay.class);

    private final TrackingWebSocketHandler wsHandler;
    private final ObjectMapper             objectMapper;

    public RedisTrackingRelay(TrackingWebSocketHandler wsHandler, ObjectMapper objectMapper) {
        this.wsHandler    = wsHandler;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);

        if (!TrackingChannels.isVehicleChannel(channel)) return;

        try {
            String              json     = new String(message.getBody(), StandardCharsets.UTF_8);
            VehicleLiveSnapshot snapshot = objectMapper.readValue(json, VehicleLiveSnapshot.class);
            String              plate    = TrackingChannels.extractPlate(channel);
            wsHandler.pushToPlate(plate, snapshot);
        } catch (Exception e) {
            log.error("Failed to relay tracking message from {}: {}", channel, e.getMessage());
        }
    }
}
