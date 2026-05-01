package ba.backend.tracking.config;

import ba.backend.tracking.websocket.LiveTrackingHandler;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.support.WebSocketHttpRequestHandler;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;

/**
 * Registers /ws/live as a raw (non-STOMP) WebSocket endpoint.
 *
 * @EnableWebSocket is intentionally NOT used here — it would create a second
 * "webSocketHandlerMapping" bean that conflicts with the one produced by
 * @EnableWebSocketMessageBroker in WebSocketConfig, silently dropping one of the
 * two handler registrations.  Instead we define a uniquely-named HandlerMapping
 * bean alongside the existing HttpRequestHandlerAdapter (auto-registered by
 * Spring MVC) to avoid any naming collision.
 */
@Configuration
public class RawWebSocketConfig {

    private final LiveTrackingHandler liveTrackingHandler;

    public RawWebSocketConfig(LiveTrackingHandler liveTrackingHandler) {
        this.liveTrackingHandler = liveTrackingHandler;
    }

    @Bean
    public SimpleUrlHandlerMapping rawWsHandlerMapping() {
        WebSocketHttpRequestHandler handler = new WebSocketHttpRequestHandler(liveTrackingHandler);
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setUrlMap(Map.of("/ws/live", handler));
        // Order 2 = just after the STOMP handler mapping (order 1), well before static resources (Integer.MAX_VALUE-1)
        mapping.setOrder(2);
        return mapping;
    }
}
