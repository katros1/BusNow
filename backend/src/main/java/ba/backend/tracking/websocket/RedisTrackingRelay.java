package ba.backend.tracking.websocket;

import ba.backend.tracking.TrackingChannels;
import ba.backend.tracking.dto.VehicleLiveSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Bridges Redis pub/sub → STOMP WebSocket.
 *
 * Listens on the Redis pattern {@code tracking:*} and forwards each message
 * to the matching STOMP topic so every subscribed WebSocket client receives it:
 *
 *   Redis  tracking:route:{routeId}       → STOMP /topic/tracking/route/{routeId}
 *   Redis  tracking:vehicle:{plateNumber} → STOMP /topic/tracking/vehicle/{plateNumber}
 */
@Component
public class RedisTrackingRelay implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisTrackingRelay.class);

    private final SimpMessagingTemplate stomp;
    private final ObjectMapper          objectMapper;

    public RedisTrackingRelay(SimpMessagingTemplate stomp, ObjectMapper objectMapper) {
        this.stomp        = stomp;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        String json    = new String(message.getBody(),    StandardCharsets.UTF_8);

        try {
            VehicleLiveSnapshot snapshot = objectMapper.readValue(json, VehicleLiveSnapshot.class);

            if (TrackingChannels.isRouteChannel(channel)) {
                String routeId = TrackingChannels.extractRouteId(channel);
                stomp.convertAndSend(TrackingChannels.routeTopic(routeId), snapshot);

            } else if (TrackingChannels.isVehicleChannel(channel)) {
                String plate = TrackingChannels.extractPlate(channel);
                stomp.convertAndSend(TrackingChannels.vehicleTopic(plate), snapshot);
            }

        } catch (Exception e) {
            log.error("Failed to relay tracking message from channel {}: {}", channel, e.getMessage());
        }
    }
}
