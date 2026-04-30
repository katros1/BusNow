package ba.backend.tracking.service;

import ba.backend.bus.entity.BusEntity;
import ba.backend.route.entity.RouteEntity;
import ba.backend.shared.exception.ResourceNotFoundException;
import ba.backend.tracking.dto.ReplayTripDto;
import ba.backend.tracking.dto.VehiclePositionEvent;
import ba.backend.tracking.entity.VehicleLocationEntity;
import ba.backend.tracking.repository.VehicleLocationRepository;
import ba.backend.tracking.websocket.LiveTrackingHandler;
import ba.backend.trip.entity.TripEntity;
import ba.backend.trip.entity.TripStatus;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class SimulatorService {

    private static final String TRACKING_TOPIC = "/topic/tracking";
    private static final double DEFAULT_SPEED = 5.0;

    private final VehicleLocationRepository locationRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final LiveTrackingHandler liveTrackingHandler;

    private final Map<UUID, Thread> activeReplays = new ConcurrentHashMap<>();

    public SimulatorService(VehicleLocationRepository locationRepository,
                            SimpMessagingTemplate messagingTemplate,
                            LiveTrackingHandler liveTrackingHandler) {
        this.locationRepository = locationRepository;
        this.messagingTemplate  = messagingTemplate;
        this.liveTrackingHandler = liveTrackingHandler;
    }

    public List<ReplayTripDto> listTrips() {
        return locationRepository.findTripsWithFrameCounts();
    }

    /**
     * Loads all recorded frames for the given trip and broadcasts them over WebSocket
     * at (original gap / speedMultiplier) ms per frame — min 200 ms, max 5 000 ms.
     * Returns the number of frames queued.
     */
    public int startReplay(UUID tripId, Double speedMultiplier) {
        stopReplay(tripId);

        List<VehicleLocationEntity> frames = locationRepository.findByTripIdWithDetails(tripId);
        if (frames.isEmpty()) {
            throw new ResourceNotFoundException("No location data found for trip " + tripId);
        }

        double mult = speedMultiplier != null ? Math.max(0.1, Math.min(100.0, speedMultiplier)) : DEFAULT_SPEED;

        Thread thread = Thread.ofVirtual().start(() -> replayFrames(tripId, frames, mult));
        activeReplays.put(tripId, thread);
        return frames.size();
    }

    public boolean stopReplay(UUID tripId) {
        Thread t = activeReplays.remove(tripId);
        if (t != null) { t.interrupt(); return true; }
        return false;
    }

    public Set<UUID> getActiveReplayIds() {
        return Set.copyOf(activeReplays.keySet());
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private void replayFrames(UUID tripId, List<VehicleLocationEntity> frames, double mult) {
        try {
            for (int i = 0; i < frames.size(); i++) {
                if (Thread.currentThread().isInterrupted()) return;

                VehicleLocationEntity loc = frames.get(i);
                VehiclePositionEvent event = toEvent(loc);

                messagingTemplate.convertAndSend(TRACKING_TOPIC, event);
                messagingTemplate.convertAndSend(TRACKING_TOPIC + "/" + loc.getBus().getId(), event);
                liveTrackingHandler.broadcast(event);

                if (i < frames.size() - 1) {
                    long gapMs = frames.get(i + 1).getRecordedAt().toEpochMilli()
                               - loc.getRecordedAt().toEpochMilli();
                    long sleepMs = Math.max(200L, Math.min(5_000L, (long) (Math.abs(gapMs) / mult)));
                    Thread.sleep(sleepMs);
                }
            }
        } catch (InterruptedException ignored) {
        } finally {
            activeReplays.remove(tripId);
        }
    }

    private VehiclePositionEvent toEvent(VehicleLocationEntity loc) {
        BusEntity bus = loc.getBus();
        TripEntity trip = loc.getTrip();
        RouteEntity route = trip != null ? trip.getRoute() : null;

        VehiclePositionEvent.RouteInfo routeInfo = null;
        if (route != null) {
            String code = route.getRouteCode() != null ? route.getRouteCode().getCode() : null;
            String dir  = route.getDirection()  != null ? route.getDirection().name()  : null;
            routeInfo = new VehiclePositionEvent.RouteInfo(route.getId(), route.getName(), code, dir);
        }

        VehiclePositionEvent.TripInfo tripInfo = null;
        if (trip != null) {
            int onBoard = loc.getPassengersOnBoard() != null ? loc.getPassengersOnBoard() : 0;
            Integer available = bus.getCapacity() != null ? Math.max(0, bus.getCapacity() - onBoard) : null;
            tripInfo = new VehiclePositionEvent.TripInfo(
                trip.getId(), TripStatus.ACTIVE, trip.getStartedAt(),
                0, 0, onBoard, available
            );
        }

        return new VehiclePositionEvent(
            bus.getId(), bus.getPlateNumber(), bus.getGpsImei(),
            true, loc.getLatitude(), loc.getLongitude(),
            loc.getSpeedKmh(), loc.getHeadingDeg(),
            loc.getRecordedAt().toString(),
            routeInfo, tripInfo
        );
    }
}
