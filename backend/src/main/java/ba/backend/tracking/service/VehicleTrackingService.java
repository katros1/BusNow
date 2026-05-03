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
                .forEach(trip -> {
                    UUID busId = trip.getBus().getId();
                    BusTrackingState state = BusTrackingState.empty()
                        .withActiveTrip(trip.getId(), trip.getStartedAt(),
                                trip.getSnapshotIn(), trip.getSnapshotOut(),
                                trip.getRoute().getId());
                    
                    // Also restore the last completed route from the bus entity if available
                    if (trip.getBus().getLastCompletedRouteId() != null) {
                        // We use reflecting or a constructor since we are in @PostConstruct
                        state = new BusTrackingState(
                            null, trip.getId(), trip.getStartedAt(),
                            trip.getSnapshotIn(), trip.getSnapshotOut(),
                            trip.getRoute().getId(), trip.getBus().getLastCompletedRouteId(),
                            null, null, null
                        );
                    }
                    busStates.put(busId, state);
                });
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

        final Double lat = parseDoubleOrNull(gps.getLatitude());
        final Double lon = parseDoubleOrNull(gps.getLongitude());
        if (lat == null || lon == null) return;

        // ── 0. Update state and position ────────────────────────────────────────
        BusTrackingState tempState = busStates.getOrDefault(bus.getId(), BusTrackingState.empty());
        
        // Resilience: if in-memory says no trip, double-check DB to avoid unique constraint hits
        if (tempState.activeTripId() == null) {
            TripEntity active = tripRepository.findByBusIdAndStatus(bus.getId(), TripStatus.ACTIVE).orElse(null);
            if (active != null) {
                tempState = tempState.withActiveTrip(active.getId(), active.getStartedAt(), 
                            active.getSnapshotIn(), active.getSnapshotOut(), active.getRoute().getId());
                busStates.put(bus.getId(), tempState);
            }
        }
        
        // Restore lastCompletedRouteId from Bus entity if it's missing in state (e.g. cold start)
        if (tempState.lastCompletedRouteId() == null && bus.getLastCompletedRouteId() != null) {
            // A bit clunky to recreate because it's a record
            tempState = new BusTrackingState(
                tempState.inBusParkId(), tempState.activeTripId(), tempState.tripStartedAt(),
                tempState.tripSnapshotIn(), tempState.tripSnapshotOut(),
                tempState.activeRouteId(), bus.getLastCompletedRouteId(),
                tempState.currentStopId(), tempState.currentStopName(), tempState.currentStopSequence()
            );
        }

        final BusTrackingState prevState = tempState;

        busRepository.updatePosition(bus.getId(), lat, lon);
        bus.setCurrentLatitude(lat);
        bus.setCurrentLongitude(lon);

        // ── 1. Bus-park geofence check ───────────────────────────────────────────
        UUID nowInBusParkId = null;
        String nowInBusParkName = "Unknown";
        for (RouteEntity route : routes) {
            try {
                // Priority A: Check the destination of our CURRENT ACTIVE route
                if (prevState.activeRouteId() != null && route.getId().equals(prevState.activeRouteId())) {
                    if (route.getEndBusPark() != null && route.getEndBusPark().getPolygon() != null
                            && geofenceService.contains(route.getEndBusPark().getPolygon(), lat, lon)) {
                        nowInBusParkId = route.getEndBusPark().getId();
                        nowInBusParkName = route.getEndBusPark().getName();
                        log.debug("Bus {} in active trip's END park: {}", bus.getPlateNumber(), nowInBusParkName);
                        break; 
                    }
                }
                // Priority B: Check if it's a start park of ANY associated route
                if (route.getStartBusPark() != null && route.getStartBusPark().getPolygon() != null
                        && geofenceService.contains(route.getStartBusPark().getPolygon(), lat, lon)) {
                    nowInBusParkId = route.getStartBusPark().getId();
                    nowInBusParkName = route.getStartBusPark().getName();
                    log.debug("Bus {} in a START park: {}", bus.getPlateNumber(), nowInBusParkName);
                }
                // Priority C: Check if it's an end park of ANY associated route
                if (nowInBusParkId == null && route.getEndBusPark() != null && route.getEndBusPark().getPolygon() != null
                        && geofenceService.contains(route.getEndBusPark().getPolygon(), lat, lon)) {
                    nowInBusParkId = route.getEndBusPark().getId();
                    nowInBusParkName = route.getEndBusPark().getName();
                    log.debug("Bus {} in an associated route's END park: {}", bus.getPlateNumber(), nowInBusParkName);
                }
            } catch (Exception e) {
                log.warn("Geofence check failed for route={}: {}", route.getId(), e.getMessage());
            }
        }

        BusTrackingState state = prevState.withGeofence(nowInBusParkId);

        // ── 2. Trip completion logic ─────────────────────────────────────────────
        if (state.activeTripId() != null && nowInBusParkId != null) {
            RouteEntity activeRoute = findById(routes, state.activeRouteId());
            
            // Log for debugging
            log.debug("Bus {} with active trip {} in park {}. Checking if terminal matches route end {}...", 
                bus.getPlateNumber(), state.activeTripId(), nowInBusParkId, 
                activeRoute != null ? activeRoute.getEndBusPark().getId() : "NULL");

            if (activeRoute != null && activeRoute.getEndBusPark() != null
                    && activeRoute.getEndBusPark().getId().equals(nowInBusParkId)) {

                int finalIn  = passengers != null ? passengers.getIn()  : state.tripSnapshotIn();
                int finalOut = passengers != null ? passengers.getOut() : state.tripSnapshotOut();

                tripService.completeTrip(state.activeTripId(), finalIn, finalOut);
                log.info("Trip COMPLETED: bus={} route={} (dir={}) terminal={}", 
                    bus.getPlateNumber(), activeRoute.getName(), activeRoute.getDirection(), nowInBusParkName);
                state = state.withTripCleared(activeRoute.getId());
            }
        }

        // ── 3. Trip start logic ──────────────────────────────────────────────────
        if (state.activeTripId() == null) {
            UUID parkJustLeftId = null;

            if (prevState.inBusParkId() != null && nowInBusParkId == null) {
                parkJustLeftId = prevState.inBusParkId();
            } else if (nowInBusParkId == null) {
                // Cold start fallback
                if (prevState.lastCompletedRouteId() != null) {
                    RouteEntity last = findById(routes, prevState.lastCompletedRouteId());
                    if (last != null && last.getEndBusPark() != null) parkJustLeftId = last.getEndBusPark().getId();
                }
                if (parkJustLeftId == null && !routes.isEmpty()) {
                    parkJustLeftId = routes.get(0).getStartBusPark().getId();
                }
            }

            if (parkJustLeftId != null) {
                final UUID parkId = parkJustLeftId;
                
                // PICK THE NEXT ROUTE (Toggle Direction)
                RouteEntity routeToStart = routes.stream()
                        .filter(r -> r.getStartBusPark() != null && r.getStartBusPark().getId().equals(parkId))
                        .filter(r -> !r.getId().equals(prevState.lastCompletedRouteId()))
                        .findFirst()
                        .orElseGet(() -> routes.stream()
                                .filter(r -> r.getStartBusPark() != null && r.getStartBusPark().getId().equals(parkId))
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

        // ── 4. Live passenger delta calculation ──────────────────────────────────
        int onBoardCalculated = 0;
        if (state.activeTripId() != null && passengers != null) {
            int inDelta  = Math.max(0, passengers.getIn()  - state.tripSnapshotIn());
            int outDelta = Math.max(0, passengers.getOut() - state.tripSnapshotOut());
            onBoardCalculated = Math.max(0, inDelta - outDelta);
            tripService.updatePassengersOnBoard(state.activeTripId(), onBoardCalculated);
        }

        // ── 5. Stop geofence check ───────────────────────────────────────────────
        UUID nowAtStopId = null;
        String nowAtStopName = null;
        Integer nowAtStopSequence = null;
        RouteEntity activeRouteObj = findById(routes, state.activeRouteId());
        if (activeRouteObj != null) {
            for (RouteStopEntity rs : activeRouteObj.getRouteStops()) {
                StopEntity stop = rs.getStop();
                try {
                    if (stop != null && stop.getGeo() != null && geofenceService.contains(stop.getGeo(), lat, lon)) {
                        nowAtStopId = stop.getId();
                        nowAtStopName = stop.getName();
                        nowAtStopSequence = rs.getSequence();
                        break;
                    }
                } catch (Exception ignored) {}
            }
        }

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
                lat, lon, parseDoubleOrNull(gps.getSpeedKmh()), parseDoubleOrNull(gps.getHeadingDeg()),
                onBoardCalculated, recordedAt);

        // ── 7. WebSocket broadcast ───────────────────────────────────────────────
        VehiclePositionEvent position = positionEvent(bus, deviceId, gps, passengers,
                payload.getDevice().getTimestamp(), state, activeRouteObj, routes);
        broadcast(bus.getId(), position);
    }

    // ── Helper methods (Internal) ──────────────────────────────────────────────

    private void broadcast(UUID busId, VehiclePositionEvent event) {
        messagingTemplate.convertAndSend(TRACKING_TOPIC, event);
        messagingTemplate.convertAndSend(TRACKING_TOPIC + "/" + busId, event);
        liveTrackingHandler.broadcast(event);
    }

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
            int curIn = passengers != null ? passengers.getIn() : state.tripSnapshotIn();
            int curOut = passengers != null ? passengers.getOut() : state.tripSnapshotOut();
            int tripIn = Math.max(0, curIn - state.tripSnapshotIn());
            int tripOut = Math.max(0, curOut - state.tripSnapshotOut());
            int onBoard = Math.max(0, tripIn - tripOut);
            Integer avail = bus.getCapacity() != null ? Math.max(0, bus.getCapacity() - onBoard) : null;

            tripInfo = new VehiclePositionEvent.TripInfo(
                    state.activeTripId(), TripStatus.ACTIVE, state.tripStartedAt(),
                    tripIn, tripOut, onBoard, avail);
        }

        VehiclePositionEvent.StopInfo stopInfo = state.currentStopId() != null
                ? new VehiclePositionEvent.StopInfo(state.currentStopId(), state.currentStopName(), state.currentStopSequence())
                : null;

        return new VehiclePositionEvent(
                bus.getId(), bus.getPlateNumber(), deviceId, true,
                parseDoubleOrNull(gps.getLatitude()), parseDoubleOrNull(gps.getLongitude()),
                parseDoubleOrNull(gps.getSpeedKmh()), parseDoubleOrNull(gps.getHeadingDeg()),
                timestamp, routeInfo(bus, activeRoute, allBusRoutes), tripInfo, stopInfo);
    }

    private VehiclePositionEvent.RouteInfo routeInfo(BusEntity bus, RouteEntity route,
            List<RouteEntity> allBusRoutes) {
        if (route != null) {
            String code = route.getRouteCode() != null ? route.getRouteCode().getCode() : null;
            String dir = route.getDirection() != null ? route.getDirection().name() : null;
            return new VehiclePositionEvent.RouteInfo(route.getId(), route.getName(), code, dir);
        }
        if (!allBusRoutes.isEmpty()) {
            RouteEntity fb = allBusRoutes.get(0);
            String code = bus.getRouteCode() != null ? bus.getRouteCode().getCode() : null;
            String dir = fb.getDirection() != null ? fb.getDirection().name() : null;
            return new VehiclePositionEvent.RouteInfo(fb.getId(), fb.getName(), code, dir);
        }
        return null;
    }

    private RouteEntity findById(List<RouteEntity> routes, UUID id) {
        if (id == null) return null;
        return routes.stream().filter(r -> r.getId().equals(id)).findFirst().orElse(null);
    }

    private Double parseDoubleOrNull(String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value)) return null;
        try { return Double.parseDouble(value); } catch (Exception e) { return null; }
    }

    private Instant parseRecordedAt(String timestamp) {
        if (timestamp == null) return Instant.now();
        try { return Instant.parse(timestamp); } catch (Exception e) {
            try { return ZonedDateTime.parse(timestamp, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant(); }
            catch (Exception e2) { return Instant.now(); }
        }
    }
}
