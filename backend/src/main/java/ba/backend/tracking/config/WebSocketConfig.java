package ba.backend.tracking.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP WebSocket configuration.
 *
 * Connect:    ws://host/ws/tracking
 * Subscribe:  /topic/tracking/route/{routeId}   – all buses on a route
 *             /topic/tracking/vehicle/{plate}    – one specific bus
 *             /user/queue/tracking               – initial snapshot on subscribe
 * Send:       /app/tracking/subscribe            – register subscription + receive initial state
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/tracking")
                .setAllowedOriginPatterns("*");
        registry.addEndpoint("/ws/tracking-sockjs")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}
