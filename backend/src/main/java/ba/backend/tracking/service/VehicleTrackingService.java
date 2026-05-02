package ba.backend.tracking.service;

import ba.backend.bus.entity.BusEntity;
import ba.backend.bus.repository.BusRepository;
import ba.backend.route.entity.RouteEntity;
import ba.backend.route.entity.RouteStopEntity;
import ba.backend.route.repository.RouteRepository;
import ba.backend.stops.entity.StopEntity;
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
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VehicleTrackingService {

    private static final Logger log = LoggerFactory.getLogger(VehicleTrackingService.class);
    private static final String TRACKING_TOPIC = "/topic/tracking";

    // ── Per-bus in-memory state ─────────────────────────────────────────────────

    private record BusTrackingState(
            UUID inBusParkId,
            UUID activeTripId,
            Instant tripStartedAt,
            int tripSnapshotIn,
            int tripSnapshotOut,
            UUID activeRouteId,
            UUID lastCompletedRouteId,
            UUID currentStopId,
            String currentStopName,
            Integer currentStopSequence) {

        static BusTrackingState empty() {
            return new BusTrackingState(null, null, null, 0, 0, null, null, null, null, null);
        }

        BusTrackingState withGeofence(UUID busParkId) {
            return new BusTrackingState(busParkId,
                    activeTripId, tripStartedAt, tripSnapshotIn, tripSnapshotOut, activeRouteId,
                    lastCompletedRouteId, currentStopId, currentStopName, currentStopSequence);
        }

        BusTrackingState withActiveTrip(UUID tripId, Instant startedAt, int snapshotIn, int snapshotOut, UUID routeId) {
            return new BusTrackingState(inBusParkId,
                    tripId, startedAt, snapshotIn, snapshotOut, routeId,
                    null, currentStopId, currentStopName, currentStopSequence);
        }

        BusTrackingState withTripCleared(UUID completedRouteId) {
            return new BusTrackingState(inBusParkId,
                    null, null, 0, 0, null,
                    completedRouteId, null, null, null);
        }

        BusTrackingState withCurrentStop(UUID stopId, String stopName, Integer stopSequence) {
            return new BusTrackingState(inBusParkId,
                    activeTripId, tripStartedAt, tripSnapshotIn, tripSnapshotOut, activeRouteId,
                    lastCompletedRouteId, stopId, stopName, stopSequence);
        }
    }

    private final Map<UUID, BusTrackingState> busStates = new ConcurrentHashMap<>();

    private final BusRepository busRepository;
    private final RouteRepository routeRepository;
    private final TripRepository tripRepository;
    private final TripService tripService;
    private final GeofenceService geofenceService;
    private final VehicleLocationService locationService;
    private final SimpMessagingTemplate messagingTemplate;
    private final LiveTrackingHandler liveTrackingHandler;

    public VehicleTrackingService(BusRepository busRepository, RouteRepository routeRepository,
            TripRepository tripRepository, TripService tripService,
            GeofenceService geofenceService, VehicleLocationService locationService,
            SimpMessagingTemplate messagingTemplate, LiveTrackingHandler liveTrackingHandler) {
        this.busRepository     = busRepository;
        this.routeRepository   = routeRepository;
        this.tripRepository    = tripRepository;
        this.tripService       = tripService;
        this.geofenceService   = geofenceService;
        this.locationService   = locationService;
        this.messagingTemplate = messagingTemplate;
        this.liveTrackingHandler = liveTrackingHandler;
    }

    @PostConstruct
    @Transactional(readOnly = true)
    public void restoreActiveTrips() {
        tripRepository.findByStatus(TripStatus.ACTIVE)
                .forEach(trip -> busStates.put(trip.getBus().getId(), BusTrackingState.empty()
                        .withActiveTrip(trip.getId(), trip.getStartedAt(),
                                trip.getSnapshotIn(), trip.getSnapshotOut(),
                                trip.getRoute().getId())));
    }

    @EventListener
    @Transactional
    public void onVehicleData(VehicleDataReceivedEvent event) {
        VehiclePayload payload = event.payload();
        if (payload.getDevice() == null) return;

        String deviceId = payload.getDevice().getId();
        BusEntity bus = busRepository.findByGpsImei(deviceId).orElse(null);
        if (bus == null) {
            log.debug("No bus registered for gpsImei={}", deviceId);
            return;
        }

        VehiclePayload.GpsData gps = payload.getGps();
        VehiclePayload.Passengers passengers = payload.getPassengers();

        List<RouteEntity> routes = bus.getRouteCode() != null
                ? routeRepository.findByRouteCodeId(bus.getRouteCode().getId())
                : List.of();

        if (gps == null || !gps.isValid()) {
            BusTrackingState state = busStates.getOrDefault(bus.getId(), BusTrackingState.empty());
            VehiclePositionEvent noFix = noFixEvent(bus, deviceId, payload.getDevice().getTimestamp(), state, routes);
            broadcast(bus.getId(), noFix);
            return;
        }

        Double lat = parseDoubleOrNull(gps.getLatitude());
        Double lon = parseDoubleOrNull(gps.getLongitude());
        if (lat == null || lon == null) return;

        // ── 0. Update state and position ────────────────────────────────────────
        BusTrackingState prevState = busStates.getOrDefault(bus.getId(), BusTrackingState.empty());
        
        // Immediately update position so that any checks use the most recent data
        busRepository.updatePosition(bus.getId(), lat, lon);
        bus.setCurrentLatitude(lat);
        bus.setCurrentLongitude(lon);

        // ── 1. Bus-park geofence check ───────────────────────────────────────────
        UUID nowInBusParkId = null;
        for (RouteEntity route : routes) {
            try {
                // If we are currently on an active trip, prioritize checking its specific destination
                if (prevState.activeRouteId() != null && route.getId().equals(prevState.activeRouteId())) {
                    if (route.getEndBusPark() != null && route.getEndBusPark().getPolygon() != null
                            && geofenceService.contains(route.getEndBusPark().getPolygon(), lat, lon)) {
                        nowInBusParkId = route.getEndBusPark().getId();
                        log.debug("Bus {} reached DESTINATION terminal for current trip: {}", 
                            bus.getPlateNumber(), route.getEndBusPark().getName());
                        break; // High priority: we reached our actual destination
                    }
                }

                // Check if it's a start park of ANY route
                if (route.getStartBusPark() != null && route.getStartBusPark().getPolygon() != null
                        && geofenceService.contains(route.getStartBusPark().getPolygon(), lat, lon)) {
                    nowInBusParkId = route.getStartBusPark().getId();
                }
                
                // Check if it's an end park of ANY route (only if start park not already found)
                if (nowInBusParkId == null && route.getEndBusPark() != null && route.getEndBusPark().getPolygon() != null
                        && geofenceService.contains(route.getEndBusPark().getPolygon(), lat, lon)) {
                    nowInBusParkId = route.getEndBusPark().getId();
                }
            } catch (Exception e) {
                log.warn("Geofence check failed for route={}: {}", route.getId(), e.getMessage());
            }
        }

        BusTrackingState state = prevState.withGeofence(nowInBusParkId);

        // ── 2. Trip start logic ──────────────────────────────────────────────────
        if (state.activeTripId() == null) {
            UUID parkLeftId = null;

            if (prevState.inBusParkId() != null && nowInBusParkId == null) {
                // Scenario A: Transitioned from inside to outside
                parkLeftId = prevState.inBusParkId();
            } else if (nowInBusParkId == null) {
                // Scenario B: Proactive start (outside and no trip)
                if (!routes.isEmpty()) {
                    parkLeftId = routes.get(0).getStartBusPark().getId();
                }
            }

            if (parkLeftId != null) {
                final UUID finalParkLeftId = parkLeftId;
                
                // Intelligent Route Selection (FORWARD / BACKWARD toggle):
                RouteEntity routeToStart = routes.stream()
                        .filter(r -> r.getStartBusPark() != null && r.getStartBusPark().getId().equals(finalParkLeftId))
                        .filter(r -> !r.getId().equals(prevState.lastCompletedRouteId()))
                        .findFirst()
                        .orElseGet(() -> routes.stream()
                                .filter(r -> r.getStartBusPark() != null && r.getStartBusPark().getId().equals(finalParkLeftId))
                                .findFirst()
                                .orElse(!routes.isEmpty() ? routes.get(0) : null));

                if (routeToStart != null) {
                    int snapshotIn  = passengers != null ? passengers.getIn()  : 0;
                    int snapshotOut = passengers != null ? passengers.getOut() : 0;
                    
                    try {
                        TripEntity trip = tripService.startTrip(bus, routeToStart, snapshotIn, snapshotOut, 0);
                        state = state.withActiveTrip(trip.getId(), trip.getStartedAt(),
                                snapshotIn, snapshotOut, routeToStart.getId());
                        log.info("Trip STARTED: bus={} route={} (dir={})", 
                            bus.getPlateNumber(), routeToStart.getName(), routeToStart.getDirection());
                    } catch (Exception e) {
                        log.error("Failed to START trip for bus {}: {}", bus.getPlateNumber(), e.getMessage());
                    }
                }
            }
        }

        // ── 3. Trip end logic ────────────────────────────────────────────────────
        if (state.activeTripId() != null && nowInBusParkId != null) {
            RouteEntity activeRoute = findById(routes, state.activeRouteId());
            if (activeRoute != null && activeRoute.getEndBusPark() != null
                    && activeRoute.getEndBusPark().getId().equals(nowInBusParkId)) {

                int finalIn  = passengers != null ? passengers.getIn()  : state.tripSnapshotIn();
                int finalOut = passengers != null ? passengers.getOut() : state.tripSnapshotOut();

                tripService.completeTrip(state.activeTripId(), finalIn, finalOut);
                log.info("Trip COMPLETED: bus={} route={} (dir={})", 
                    bus.getPlateNumber(), activeRoute.getName(), activeRoute.getDirection());
                state = state.withTripCleared(activeRoute.getId());
            }
        }

        // ── 4. Live passenger count calculation (CUMULATIVE DELTA) ───────────────
        if (state.activeTripId() != null && passengers != null) {
            int currentTripIn  = Math.max(0, passengers.getIn() - state.tripSnapshotIn());
            int currentTripOut = Math.max(0, passengers.getOut() - state.tripSnapshotOut());
            int onBoard        = Math.max(0, currentTripIn - currentTripOut);
            
            tripService.updatePassengersOnBoard(state.activeTripId(), onBoard);
        }

        // ── 5. Stop geofence check ───────────────────────────────────────────────
        UUID nowAtStopId = null;
        String nowAtStopName = null;
        Integer nowAtStopSequence = null;
        RouteEntity activeRouteForStops = findById(routes, state.activeRouteId());
        if (activeRouteForStops != null) {
            for (RouteStopEntity routeStop : activeRouteForStops.getRouteStops()) {
                StopEntity stop = routeStop.getStop();
                try {
                    if (stop != null && stop.getGeo() != null
                            && geofenceService.contains(stop.getGeo(), lat, lon)) {
                        nowAtStopId       = stop.getId();
                        nowAtStopName     = stop.getName();
                        nowAtStopSequence = routeStop.getSequence();
                        break;
                    }
                } catch (Exception e) {
                    log.warn("Stop geofence check failed for stop={}: {}", stop.getId(), e.getMessage());
                }
            }
        }

        // Log stop arrival / departure transitions
        if (nowAtStopId != null && !nowAtStopId.equals(prevState.currentStopId())) {
            log.info("Bus {} ARRIVED at stop '{}' (seq={})", bus.getPlateNumber(), nowAtStopName, nowAtStopSequence);
        } else if (nowAtStopId == null && prevState.currentStopId() != null) {
            log.info("Bus {} DEPARTED stop '{}'", bus.getPlateNumber(), prevState.currentStopName());
        }

        state = state.withCurrentStop(nowAtStopId, nowAtStopName, nowAtStopSequence);

        // ── 6. Persist state and record position ─────────────────────────────────
        busStates.put(bus.getId(), state);

        Instant recordedAt = parseRecordedAt(payload.getDevice().getTimestamp());
        locationService.record(bus.getId(), state.activeTripId(), nowAtStopId,
                lat, lon,
                parseDoubleOrNull(gps.getSpeedKmh()),
                parseDoubleOrNull(gps.getHeadingDeg()),
                passengers != null ? passengers.getRemaining() : null,
                recordedAt);

        // ── 7. WebSocket broadcast ───────────────────────────────────────────────
        VehiclePositionEvent position = positionEvent(bus, deviceId, gps, passengers,
                payload.getDevice().getTimestamp(), state, activeRouteForStops, routes);
        broadcast(bus.getId(), position);
    }

    // ── Broadcast helpers ────────────────────────────────────────────────────────

    private void broadcast(UUID busId, VehiclePositionEvent event) {
        messagingTemplate.convertAndSend(TRACKING_TOPIC, event);
        messagingTemplate.convertAndSend(TRACKING_TOPIC + "/" + busId, event);
        liveTrackingHandler.broadcast(event);
    }

    // ── Event builders ───────────────────────────────────────────────────────────

    private VehiclePositionEvent noFixEvent(BusEntity bus, String deviceId, String timestamp,
            BusTrackingState state, List<RouteEntity> allBusRoutes) {
        return new VehiclePositionEvent(bus.getId(), bus.getPlateNumber(), deviceId,
                false, null, null, null, null, timestamp,
                routeInfo(bus, null, allBusRoutes), null, null);
    }

    private VehiclePositionEvent positionEvent(BusEntity bus, String deviceId,
            VehiclePayload.GpsData gps, VehiclePayload.Passengers passengers,
            String timestamp, BusTrackingState state,
            RouteEntity activeRoute, List<RouteEntity> allBusRoutes) {

        VehiclePositionEvent.TripInfo tripInfo = null;
        if (state.activeTripId() != null) {
            int currentIn  = passengers != null ? passengers.getIn()  : state.tripSnapshotIn();
            int currentOut = passengers != null ? passengers.getOut() : state.tripSnapshotOut();
            
            // Calculate trip-specific counts
            int tripIn  = Math.max(0, currentIn  - state.tripSnapshotIn());
            int tripOut = Math.max(0, currentOut - state.tripSnapshotOut());
            int onBoard = Math.max(0, tripIn - tripOut);
            
            Integer available = bus.getCapacity() != null ? Math.max(0, bus.getCapacity() - onBoard) : null;

            tripInfo = new VehiclePositionEvent.TripInfo(
                    state.activeTripId(), TripStatus.ACTIVE, state.tripStartedAt(),
                    tripIn, tripOut, onBoard, available);
        }

        VehiclePositionEvent.StopInfo stopInfo = state.currentStopId() != null
                ? new VehiclePositionEvent.StopInfo(
                        state.currentStopId(), state.currentStopName(), state.currentStopSequence())
                : null;

        return new VehiclePositionEvent(
                bus.getId(), bus.getPlateNumber(), deviceId, true,
                parseDoubleOrNull(gps.getLatitude()), parseDoubleOrNull(gps.getLongitude()),
                parseDoubleOrNull(gps.getSpeedKmh()), parseDoubleOrNull(gps.getHeadingDeg()),
                timestamp, routeInfo(bus, activeRoute, allBusRoutes), tripInfo, stopInfo);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private VehiclePositionEvent.RouteInfo routeInfo(BusEntity bus, RouteEntity route,
            List<RouteEntity> allBusRoutes) {
        if (route != null) {
            String code      = route.getRouteCode() != null ? route.getRouteCode().getCode() : null;
            String direction = route.getDirection() != null ? route.getDirection().name() : null;
            return new VehiclePositionEvent.RouteInfo(route.getId(), route.getName(), code, direction);
        }
        if (!allBusRoutes.isEmpty()) {
            RouteEntity fallback = allBusRoutes.get(0);
            String code      = bus.getRouteCode() != null ? bus.getRouteCode().getCode() : null;
            String direction = fallback.getDirection() != null ? fallback.getDirection().name() : null;
            return new VehiclePositionEvent.RouteInfo(fallback.getId(), fallback.getName(), code, direction);
        }
        if (bus.getRouteCode() != null) {
            return new VehiclePositionEvent.RouteInfo(null, null, bus.getRouteCode().getCode(), null);
        }
        return null;
    }

    private RouteEntity findById(List<RouteEntity> routes, UUID id) {
        if (id == null) return null;
        return routes.stream().filter(r -> r.getId().equals(id)).findFirst().orElse(null);
    }

    private Double parseDoubleOrNull(String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value)) return null;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Instant parseRecordedAt(String timestamp) {
        if (timestamp == null) return Instant.now();
        try {
            return Instant.parse(timestamp);
        } catch (Exception e) {
            try {
                return ZonedDateTime.parse(timestamp, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant();
            } catch (Exception e2) {
                return Instant.now();
            }
        }
    }
}
