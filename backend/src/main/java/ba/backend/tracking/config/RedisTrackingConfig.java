package ba.backend.tracking.config;

import ba.backend.tracking.websocket.RedisTrackingRelay;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Wires up Redis pub/sub for the live tracking pipeline.
 *
 * The pattern {@code tracking:*} covers both channel families:
 *   tracking:route:{routeId}
 *   tracking:vehicle:{plateNumber}
 *
 * RedisTrackingRelay receives messages and forwards them to STOMP topics.
 */
@Configuration
public class RedisTrackingConfig {

    @Bean
    public RedisMessageListenerContainer redisListenerContainer(
            RedisConnectionFactory connectionFactory, RedisTrackingRelay relay) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(relay, new PatternTopic("tracking:*"));
        return container;
    }
}
