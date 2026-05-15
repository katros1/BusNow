package ba.backend.tracking.service;

import ba.backend.tracking.TrackingChannels;
import ba.backend.tracking.dto.VehicleLiveSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Publishes a VehicleLiveSnapshot to two Redis channels:
 *   tracking:route:{routeId}       → all subscribers watching a route
 *   tracking:vehicle:{plateNumber} → subscribers watching a specific bus
 *
 * RedisTrackingRelay listens on these channels and forwards to STOMP topics.
 */
@Service
public class TrackingPublisher {

    private static final Logger log = LoggerFactory.getLogger(TrackingPublisher.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public TrackingPublisher(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis        = redis;
        this.objectMapper = objectMapper;
    }

    public void publish(VehicleLiveSnapshot snapshot) {
        try {
            String json = objectMapper.writeValueAsString(snapshot);
            if (snapshot.routeId() != null) {
                redis.convertAndSend(TrackingChannels.routeChannel(snapshot.routeId().toString()), json);
            }
            redis.convertAndSend(TrackingChannels.vehicleChannel(snapshot.plateNumber()), json);
        } catch (Exception e) {
            log.error("Failed to publish snapshot for bus {}: {}", snapshot.plateNumber(), e.getMessage());
        }
    }
}
