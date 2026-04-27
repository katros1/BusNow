package ba.backend.tracking.config;

import ba.backend.tracking.websocket.LiveTrackingHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class RawWebSocketConfig implements WebSocketConfigurer {

    private final LiveTrackingHandler liveTrackingHandler;

    public RawWebSocketConfig(LiveTrackingHandler liveTrackingHandler) {
        this.liveTrackingHandler = liveTrackingHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(liveTrackingHandler, "/ws/live").setAllowedOrigins("*");
    }
}
