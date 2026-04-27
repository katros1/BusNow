package ba.backend.tracking.service;

import ba.backend.bus.entity.BusEntity;
import ba.backend.bus.repository.BusRepository;
import ba.backend.route.entity.RouteEntity;
import ba.backend.route.repository.RouteRepository;
import ba.backend.tracking.dto.VehiclePositionEvent;
import ba.backend.tracking.event.VehicleDataReceivedEvent;
import ba.backend.tracking.websocket.LiveTrackingHandler;
import ba.backend.trip.entity.TripEntity;
import ba.backend.trip.entity.TripStatus;
import ba.backend.trip.repository.TripRepository;
import ba.backend.trip.service.TripService;
import ba.backend.vehicledata.model.VehiclePayload;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class VehicleTrackingService {

    private static final String TRACKING_TOPIC = "/topic/tracking";

    private record BusTrackingState(
            UUID inStartParkOfRouteId,
            UUID inEndParkOfRouteId,
            UUID activeTripId,
            Instant tripStartedAt,
            int tripSnapshotIn,
            int tripSnapshotOut,
            UUID activeRouteId) {
        static BusTrackingState empty() {
            return new BusTrackingState(null, null, null, null, 0, 0, null);
        }

        BusTrackingState withGeofence(UUID startParkRouteId, UUID endParkRouteId) {
            return new BusTrackingState(startParkRouteId, endParkRouteId,
                    activeTripId, tripStartedAt, tripSnapshotIn, tripSnapshotOut, activeRouteId);
        }

        BusTrackingState withActiveTrip(UUID tripId, Instant startedAt, int snapshotIn, int snapshotOut, UUID routeId) {
            return new BusTrackingState(inStartParkOfRouteId, inEndParkOfRouteId,
                    tripId, startedAt, snapshotIn, snapshotOut, routeId);
        }

        BusTrackingState withTripCleared() {
            return new BusTrackingState(inStartParkOfRouteId, inEndParkOfRouteId,
                    null, null, 0, 0, null);
        }
    }

    private final Map<UUID, BusTrackingState> busStates = new ConcurrentHashMap<>();

    private final BusRepository busRepository;
    private final RouteRepository routeRepository;
    private final TripRepository tripRepository;
    private final TripService tripService;
    private final GeofenceService geofenceService;
    private final SimpMessagingTemplate messagingTemplate;
    private final LiveTrackingHandler liveTrackingHandler;

    public VehicleTrackingService(BusRepository busRepository, RouteRepository routeRepository,
            TripRepository tripRepository, TripService tripService,
            GeofenceService geofenceService, SimpMessagingTemplate messagingTemplate,
            LiveTrackingHandler liveTrackingHandler) {
        this.busRepository = busRepository;
        this.routeRepository = routeRepository;
        this.tripRepository = tripRepository;
        this.tripService = tripService;
        this.geofenceService = geofenceService;
        this.messagingTemplate = messagingTemplate;
        this.liveTrackingHandler = liveTrackingHandler;
    }

    @PostConstruct
    public void restoreActiveTrips() {
        tripRepository.findByStatus(TripStatus.ACTIVE)
                .forEach(trip -> busStates.put(trip.getBus().getId(), BusTrackingState.empty()
                        .withActiveTrip(trip.getId(), trip.getStartedAt(),
                                trip.getSnapshotIn(), trip.getSnapshotOut(),
                                trip.getRoute().getId())));
    }

    @EventListener
    public void onVehicleData(VehicleDataReceivedEvent event) {
        VehiclePayload payload = event.payload();
        if (payload.getDevice() == null)
            return;

        String deviceId = payload.getDevice().getId();
        BusEntity bus = busRepository.findByGpsImei(deviceId).orElse(null);
        if (bus == null)
            return;

        VehiclePayload.GpsData gps = payload.getGps();
        VehiclePayload.Passengers passengers = payload.getPassengers();
        BusTrackingState state = busStates.getOrDefault(bus.getId(), BusTrackingState.empty());

        if (gps == null || !gps.isValid()) {
            VehiclePositionEvent noFix = noFixEvent(bus, deviceId, payload.getDevice().getTimestamp(), state);
            messagingTemplate.convertAndSend(TRACKING_TOPIC, noFix);
            liveTrackingHandler.broadcast(noFix);
            return;
        }

        Double lat = parseDoubleOrNull(gps.getLatitude());
        Double lon = parseDoubleOrNull(gps.getLongitude());
        if (lat == null || lon == null)
            return;

        List<RouteEntity> routes = bus.getRouteCode() != null
                ? routeRepository.findByRouteCodeId(bus.getRouteCode().getId())
                : List.of();

        UUID nowInStartParkRouteId = null;
        UUID nowInEndParkRouteId = null;
        for (RouteEntity route : routes) {
            if (geofenceService.contains(route.getStartBusPark().getPolygon(), lat, lon)) {
                nowInStartParkRouteId = route.getId();
            }
            if (geofenceService.contains(route.getEndBusPark().getPolygon(), lat, lon)) {
                nowInEndParkRouteId = route.getId();
            }
        }

        state = state.withGeofence(nowInStartParkRouteId, nowInEndParkRouteId);

        // Trip start: bus just exited a startBusPark
        BusTrackingState prevState = busStates.getOrDefault(bus.getId(), BusTrackingState.empty());
        if (prevState.inStartParkOfRouteId() != null
                && nowInStartParkRouteId == null
                && state.activeTripId() == null
                && passengers != null) {
            RouteEntity route = findById(routes, prevState.inStartParkOfRouteId());
            if (route != null) {
                TripEntity trip = tripService.startTrip(bus, route,
                        passengers.getIn(), passengers.getOut(), passengers.getRemaining());
                state = state.withActiveTrip(trip.getId(), trip.getStartedAt(),
                        passengers.getIn(), passengers.getOut(), route.getId());
            }
        }

        // Trip end: bus just entered the endBusPark
        if (state.activeTripId() != null
                && nowInEndParkRouteId != null
                && prevState.inEndParkOfRouteId() == null) {
            tripService.completeTrip(state.activeTripId());
            state = state.withTripCleared();
        }

        // Keep live passenger count in sync
        if (state.activeTripId() != null && passengers != null) {
            tripService.updatePassengersOnBoard(state.activeTripId(), passengers.getRemaining());
        }

        busStates.put(bus.getId(), state);
        busRepository.updatePosition(bus.getId(), lat, lon);

        RouteEntity activeRoute = findById(routes, state.activeRouteId());
        VehiclePositionEvent position = positionEvent(bus, deviceId, gps, passengers,
                payload.getDevice().getTimestamp(), state, activeRoute);
        messagingTemplate.convertAndSend(TRACKING_TOPIC, position);
        liveTrackingHandler.broadcast(position);
    }

    // ── Event builders ──────────────────────────────────────────────────────────

    private VehiclePositionEvent noFixEvent(BusEntity bus, String deviceId, String timestamp,
            BusTrackingState state) {
        return new VehiclePositionEvent(bus.getId(), bus.getPlateNumber(), deviceId,
                false, null, null, null, null, timestamp,
                routeInfo(bus, null), null);
    }

    private VehiclePositionEvent positionEvent(BusEntity bus, String deviceId,
            VehiclePayload.GpsData gps,
            VehiclePayload.Passengers passengers,
            String timestamp,
            BusTrackingState state,
            RouteEntity activeRoute) {
        VehiclePositionEvent.TripInfo tripInfo = null;
        if (state.activeTripId() != null && passengers != null) {
            int tripIn = Math.max(0, passengers.getIn() - state.tripSnapshotIn());
            int tripOut = Math.max(0, passengers.getOut() - state.tripSnapshotOut());
            int onBoard = passengers.getRemaining();
            Integer available = bus.getCapacity() != null ? Math.max(0, bus.getCapacity() - onBoard) : null;
            tripInfo = new VehiclePositionEvent.TripInfo(
                    state.activeTripId(), TripStatus.ACTIVE, state.tripStartedAt(),
                    tripIn, tripOut, onBoard, available);
        }

        return new VehiclePositionEvent(
                bus.getId(), bus.getPlateNumber(), deviceId, true,
                parseDoubleOrNull(gps.getLatitude()), parseDoubleOrNull(gps.getLongitude()),
                parseDoubleOrNull(gps.getSpeedKmh()), parseDoubleOrNull(gps.getHeadingDeg()),
                timestamp, routeInfo(bus, activeRoute), tripInfo);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private VehiclePositionEvent.RouteInfo routeInfo(BusEntity bus, RouteEntity route) {
        if (route != null) {
            String code = route.getRouteCode() != null ? route.getRouteCode().getCode() : null;
            String direction = route.getDirection() != null ? route.getDirection().name() : null;
            return new VehiclePositionEvent.RouteInfo(route.getId(), route.getName(), code, direction);
        }
        if (bus.getRouteCode() != null) {
            return new VehiclePositionEvent.RouteInfo(null, null, bus.getRouteCode().getCode(), null);
        }
        return null;
    }

    private RouteEntity findById(List<RouteEntity> routes, UUID id) {
        if (id == null)
            return null;
        return routes.stream().filter(r -> r.getId().equals(id)).findFirst().orElse(null);
    }

    private Double parseDoubleOrNull(String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value))
            return null;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
