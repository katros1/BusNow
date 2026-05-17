package ba.backend.tracking.config;

import ba.backend.tracking.websocket.TrackingWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Raw WebSocket endpoint — no STOMP, no SockJS.
 *
 * Connect:  ws://host/ws/tracking
 *
 * Client → Server JSON frames:
 *   { "type": "subscribe", "plates": ["ABC-123", ...] }
 *   { "type": "ping" }
 *
 * Server → Client JSON frames:
 *   { "type": "connected" }
 *   { "type": "snapshot", "data": { ...VehicleLiveSnapshot } }
 *   { "type": "pong" }
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final TrackingWebSocketHandler handler;

    public WebSocketConfig(TrackingWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/tracking")
                .setAllowedOriginPatterns("*");
    }
}
