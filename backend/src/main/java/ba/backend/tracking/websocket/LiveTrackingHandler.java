package ba.backend.tracking.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class LiveTrackingHandler extends TextWebSocketHandler {

    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();
    private final ObjectMapper objectMapper;

    public LiveTrackingHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    public void broadcast(Object payload) {
        if (sessions.isEmpty()) return;
        try {
            String json = objectMapper.writeValueAsString(payload);
            TextMessage message = new TextMessage(json);
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    try {
                        session.sendMessage(message);
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
    }
}
