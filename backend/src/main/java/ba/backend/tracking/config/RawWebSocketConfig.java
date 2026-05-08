package ba.backend.tracking.config;

import ba.backend.tracking.websocket.LiveTrackingHandler;
import java.util.Map;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.support.WebSocketHttpRequestHandler;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;
import org.springframework.web.cors.CorsConfiguration;

@Configuration
public class RawWebSocketConfig {

    private final LiveTrackingHandler liveTrackingHandler;
    private final AuthHandshakeInterceptor authHandshakeInterceptor;

    public RawWebSocketConfig(LiveTrackingHandler liveTrackingHandler, AuthHandshakeInterceptor authHandshakeInterceptor) {
        this.liveTrackingHandler = liveTrackingHandler;
        this.authHandshakeInterceptor = authHandshakeInterceptor;
    }

    @Bean
    public SimpleUrlHandlerMapping rawWsHandlerMapping() {
        WebSocketHttpRequestHandler handler = new WebSocketHttpRequestHandler(liveTrackingHandler);
        handler.setHandshakeInterceptors(List.of(authHandshakeInterceptor));
        
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setUrlMap(Map.of("/ws/live", handler));
        
        // Setup CORS for the raw WebSocket handshake
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173"));
        config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        mapping.setCorsConfigurations(Map.of("/ws/live", config));
        
        mapping.setOrder(2);
        return mapping;
    }
}
