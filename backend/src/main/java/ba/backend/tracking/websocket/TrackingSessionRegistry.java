package ba.backend.tracking.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry of active WebSocket sessions and their plate subscriptions.
 *
 * Invariant: every sessionId in plateSessions also exists in sessions,
 *            and every sessionId in sessionPlates also exists in sessions.
 */
@Component
public class TrackingSessionRegistry {

    private final ConcurrentHashMap<String, WebSocketSession> sessions     = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>>      plateSubs    = new ConcurrentHashMap<>(); // plate → sessionIds
    private final ConcurrentHashMap<String, Set<String>>      sessionSubs  = new ConcurrentHashMap<>(); // sessionId → plates
    private final ConcurrentHashMap<String, Set<String>>      routeSubs    = new ConcurrentHashMap<>(); // routeId → sessionIds
    private final ConcurrentHashMap<String, Set<String>>      sessionRoutes = new ConcurrentHashMap<>(); // sessionId → routeIds

    public void register(WebSocketSession session) {
        sessions.put(session.getId(), session);
        sessionSubs.put(session.getId(), ConcurrentHashMap.newKeySet());
        sessionRoutes.put(session.getId(), ConcurrentHashMap.newKeySet());
    }

    public void unregister(String sessionId) {
        sessions.remove(sessionId);
        Set<String> plates = sessionSubs.remove(sessionId);
        if (plates != null) {
            for (String plate : plates) {
                Set<String> sids = plateSubs.get(plate);
                if (sids != null) sids.remove(sessionId);
            }
        }
        Set<String> routes = sessionRoutes.remove(sessionId);
        if (routes != null) {
            for (String routeId : routes) {
                Set<String> sids = routeSubs.get(routeId);
                if (sids != null) sids.remove(sessionId);
            }
        }
    }

    public void subscribe(String sessionId, String plateNumber) {
        String plate = plateNumber.toUpperCase();
        Set<String> forSession = sessionSubs.get(sessionId);
        if (forSession == null) return;
        forSession.add(plate);
        plateSubs.computeIfAbsent(plate, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
    }

    public void subscribeRoute(String sessionId, String routeId) {
        Set<String> forSession = sessionRoutes.get(sessionId);
        if (forSession == null) return;
        forSession.add(routeId);
        routeSubs.computeIfAbsent(routeId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
    }

    /** Returns a snapshot copy of open sessions subscribed to this routeId. Safe to iterate. */
    public Set<WebSocketSession> getSessionsForRoute(String routeId) {
        Set<String> ids = routeSubs.get(routeId);
        if (ids == null || ids.isEmpty()) return Collections.emptySet();
        Set<WebSocketSession> result = new HashSet<>();
        for (String id : new HashSet<>(ids)) {
            WebSocketSession s = sessions.get(id);
            if (s != null && s.isOpen()) result.add(s);
        }
        return result;
    }

    /** Returns a snapshot copy of open sessions subscribed to this plate. Safe to iterate. */
    public Set<WebSocketSession> getSessionsForPlate(String plateNumber) {
        String plate = plateNumber.toUpperCase();
        Set<String> ids = plateSubs.get(plate);
        if (ids == null || ids.isEmpty()) return Collections.emptySet();

        // Snapshot the id set to avoid ConcurrentModificationException
        Set<WebSocketSession> result = new HashSet<>();
        for (String id : new HashSet<>(ids)) {
            WebSocketSession s = sessions.get(id);
            if (s != null && s.isOpen()) result.add(s);
        }
        return result;
    }

    public WebSocketSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    /** Returns a snapshot of all currently open sessions. Safe to iterate. */
    public java.util.Collection<WebSocketSession> getAllSessions() {
        return java.util.List.copyOf(sessions.values());
    }
}
