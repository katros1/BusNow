package ba.backend.tracking.service;

import ba.backend.bus.entity.BusEntity;
import ba.backend.bus.repository.BusRepository;
import ba.backend.route.entity.RouteDirection;
import ba.backend.route.entity.RouteEntity;
import ba.backend.route.repository.RouteRepository;
import ba.backend.tracking.dto.VehicleLiveSnapshot;
import ba.backend.tracking.event.VehicleDataReceivedEvent;
import ba.backend.trip.entity.TripEntity;
import ba.backend.trip.entity.TripStatus;
import ba.backend.trip.repository.TripRepository;
import ba.backend.trip.service.TripService;
import ba.backend.vehicledata.model.VehiclePayload;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Core GPS ingestion service.
 *
 * Flow:  VehicleDataReceivedEvent
 *        → resolve bus & state
 *        → geofence: trip start / complete
 *        → stop detection
 *        → passenger count
 *        → build VehicleLiveSnapshot
 *        → publish to Redis (immediate broadcast)
 *        → async DB persist
 */
@Service
public class GpsIngestService {

    private static final Logger log = LoggerFactory.getLogger(GpsIngestService.class);

    private final ConcurrentHashMap<UUID, BusState> busStates = new ConcurrentHashMap<>();

    private final BusRepository          busRepository;
    private final RouteRepository        routeRepository;
    private final TripRepository         tripRepository;
    private final TripService            tripService;
    private final GeofenceService        geofenceService;
    private final StopResolverService    stopResolver;
    private final VehicleLocationService locationService;
    private final TrackingPublisher      publisher;

    public GpsIngestService(BusRepository busRepository, RouteRepository routeRepository,
            TripRepository tripRepository, TripService tripService,
            GeofenceService geofenceService, StopResolverService stopResolver,
            VehicleLocationService locationService, TrackingPublisher publisher) {
        this.busRepository   = busRepository;
        this.routeRepository = routeRepository;
        this.tripRepository  = tripRepository;
        this.tripService     = tripService;
        this.geofenceService = geofenceService;
        this.stopResolver    = stopResolver;
        this.locationService = locationService;
        this.publisher       = publisher;
    }

    // ── Startup: restore active trips from DB ─────────────────────────────────

    @EventListener(ApplicationReadyEvent.class)
    public void restoreActiveTrips() {
        tripRepository.findByStatus(TripStatus.ACTIVE).forEach(trip -> {
            UUID busId = trip.getBus().getId();
            BusState state = BusState.empty()
                    .withTrip(trip.getId(), trip.getRoute().getId(),
                            trip.getSnapshotIn(), trip.getSnapshotOut());
            UUID lastRoute = trip.getBus().getLastCompletedRouteId();
            if (lastRoute != null) {
                state = new BusState(state.activeTripId(), state.activeRouteId(), lastRoute,
                        null, state.snapshotIn(), state.snapshotOut(), 0, null, null);
            }
            busStates.put(busId, state);
        });
        log.info("Restored {} active trips from DB", busStates.size());
    }

    // ── Main entry point ──────────────────────────────────────────────────────

    @EventListener
    public void onVehicleData(VehicleDataReceivedEvent event) {
        VehiclePayload payload = event.payload();
        if (payload.getDevice() == null) return;

        BusEntity bus = busRepository.findByGpsImei(payload.getDevice().getId()).orElse(null);
        if (bus == null) {
            log.debug("No bus for IMEI={}", payload.getDevice().getId());
            return;
        }

        List<RouteEntity> routes = bus.getRouteCode() != null
                ? routeRepository.findByRouteCodeId(bus.getRouteCode().getId())
                : List.of();

        VehiclePayload.GpsData  gps       = payload.getGps();
        VehiclePayload.Passengers pax     = payload.getPassengers();
        Instant                 recordedAt = parseInstant(payload.getDevice().getTimestamp());

        BusState state = busStates.getOrDefault(bus.getId(), BusState.empty());
        state = syncFromDb(bus, state);

        // ── GPS invalid: publish no-fix snapshot ──────────────────────────────
        if (gps == null || !gps.isValid()) {
            VehicleLiveSnapshot snap = buildNoFixSnapshot(bus, routes, state, recordedAt);
            publisher.publish(snap);
            busStates.put(bus.getId(), state.withSeen(recordedAt, snap));
            return;
        }

        Double lat = parseDouble(gps.getLatitude());
        Double lon = parseDouble(gps.getLongitude());
        if (lat == null || lon == null) return;

        busRepository.updatePosition(bus.getId(), lat, lon);

        // ── Trip lifecycle ────────────────────────────────────────────────────
        UUID nowInParkId = parkGeofenceCheck(lat, lon, state, routes);
        state = state.withGeofence(nowInParkId);
        state = handleTripCompletion(state, nowInParkId, pax, bus, routes);
        state = handleTripStart(state, nowInParkId, pax, bus, routes);

        // ── Passengers ───────────────────────────────────────────────────────
        int onBoard = calculateOnBoard(state, pax);
        if (state.activeTripId() != null) {
            tripService.updatePassengersOnBoard(state.activeTripId(), onBoard);
        }

        // ── Stop detection ────────────────────────────────────────────────────
        RouteEntity activeRoute = findActiveRoute(state, routes);
        StopResolverService.StopResolution stopRes = activeRoute != null
                ? stopResolver.resolve(activeRoute.getRouteStops(), lat, lon, state.lastPassedStopSeq())
                : StopResolverService.StopResolution.none(state.lastPassedStopSeq());
        state = state.withStop(stopRes.updatedLastPassedSequence());

        // ── Build & broadcast ─────────────────────────────────────────────────
        VehicleLiveSnapshot snapshot = buildSnapshot(bus, activeRoute, routes, state, gps, stopRes, onBoard, recordedAt);
        publisher.publish(snapshot);
        busStates.put(bus.getId(), state.withSeen(recordedAt, snapshot));

        // ── Async DB persist (after broadcast) ────────────────────────────────
        locationService.record(bus.getId(), state.activeTripId(), stopRes.currentStopId(),
                lat, lon, parseDouble(gps.getSpeedKmh()), parseDouble(gps.getHeadingDeg()),
                onBoard, recordedAt);
    }

    // ── Expose current state (for initial WebSocket snapshot & stale checker) ─

    public Map<UUID, BusState> getBusStates() {
        return Collections.unmodifiableMap(busStates);
    }

    // ── Trip lifecycle helpers ────────────────────────────────────────────────

    private BusState handleTripCompletion(BusState state, UUID nowInParkId,
            VehiclePayload.Passengers pax, BusEntity bus, List<RouteEntity> routes) {
        if (state.activeTripId() == null || nowInParkId == null) return state;

        RouteEntity activeRoute = findActiveRoute(state, routes);
        if (activeRoute == null || activeRoute.getEndBusPark() == null) return state;
        if (!nowInParkId.equals(activeRoute.getEndBusPark().getId())) return state;

        int finalIn  = pax != null ? pax.getIn()  : state.snapshotIn();
        int finalOut = pax != null ? pax.getOut() : state.snapshotOut();
        tripService.completeTrip(state.activeTripId(), finalIn, finalOut);
        log.info("Trip {} completed — bus {} arrived at terminal", state.activeTripId(), bus.getPlateNumber());
        return state.withTripCleared(activeRoute.getId());
    }

    private BusState handleTripStart(BusState state, UUID nowInParkId,
            VehiclePayload.Passengers pax, BusEntity bus, List<RouteEntity> routes) {
        if (state.activeTripId() != null || routes.isEmpty()) return state;

        UUID parkJustLeft = resolveDepartedPark(state, nowInParkId, routes);
        if (parkJustLeft == null) return state;

        RouteEntity toStart = pickRouteFromPark(parkJustLeft, state.lastCompletedRouteId(), routes);
        if (toStart == null) return state;

        int snapIn  = pax != null ? pax.getIn()  : 0;
        int snapOut = pax != null ? pax.getOut() : 0;
        try {
            TripEntity trip = tripService.startTrip(bus, toStart, snapIn, snapOut, 0);
            log.info("Trip {} started — bus {} → route {}", trip.getId(), bus.getPlateNumber(), toStart.getName());
            return state.withTrip(trip.getId(), toStart.getId(), snapIn, snapOut);
        } catch (Exception e) {
            log.error("Failed to start trip for bus {}: {}", bus.getPlateNumber(), e.getMessage());
            return state;
        }
    }

    private UUID resolveDepartedPark(BusState state, UUID nowInParkId, List<RouteEntity> routes) {
        // Bus was in a park and just left (or moved to a different park)
        if (state.inBusParkId() != null
                && (nowInParkId == null || !nowInParkId.equals(state.inBusParkId()))) {
            return state.inBusParkId();
        }
        // Cold-start fallback: outside any park, determine likely origin
        if (nowInParkId == null) {
            if (state.lastCompletedRouteId() != null) {
                RouteEntity last = routes.stream()
                        .filter(r -> r.getId().equals(state.lastCompletedRouteId()))
                        .findFirst().orElse(null);
                if (last != null && last.getEndBusPark() != null) return last.getEndBusPark().getId();
            }
            if (!routes.isEmpty() && routes.get(0).getStartBusPark() != null) {
                return routes.get(0).getStartBusPark().getId();
            }
        }
        return null;
    }

    /**
     * Picks the next route to start when a bus departs {@code fromParkId}.
     *
     * <p>Strategy (in priority order):
     * <ol>
     *   <li>Direction toggle – if last route was FORWARD pick BACKWARD and vice versa,
     *       provided it starts from {@code fromParkId}.</li>
     *   <li>Exclusion fallback – any route from {@code fromParkId} that is NOT the last completed one.</li>
     *   <li>Last resort – any route starting from {@code fromParkId}.</li>
     * </ol>
     */
    private RouteEntity pickRouteFromPark(UUID fromParkId, UUID lastCompletedRouteId,
            List<RouteEntity> routes) {

        // 1. Direction-aware toggle
        if (lastCompletedRouteId != null) {
            RouteDirection lastDir = routes.stream()
                    .filter(r -> r.getId().equals(lastCompletedRouteId))
                    .map(RouteEntity::getDirection)
                    .findFirst().orElse(null);

            if (lastDir != null) {
                RouteDirection nextDir = lastDir == RouteDirection.FORWARD
                        ? RouteDirection.BACKWARD : RouteDirection.FORWARD;
                RouteEntity byDir = routes.stream()
                        .filter(r -> r.getDirection() == nextDir
                                && r.getStartBusPark() != null
                                && r.getStartBusPark().getId().equals(fromParkId))
                        .findFirst().orElse(null);
                if (byDir != null) return byDir;
            }
        }

        // 2. Exclusion fallback (any route from this park except the last one)
        return routes.stream()
                .filter(r -> r.getStartBusPark() != null && r.getStartBusPark().getId().equals(fromParkId))
                .filter(r -> !r.getId().equals(lastCompletedRouteId))
                .findFirst()
                // 3. Last resort – same park even if it was the last completed
                .orElseGet(() -> routes.stream()
                        .filter(r -> r.getStartBusPark() != null
                                && r.getStartBusPark().getId().equals(fromParkId))
                        .findFirst().orElse(null));
    }

    // ── Geofence helper ───────────────────────────────────────────────────────

    private UUID parkGeofenceCheck(double lat, double lon, BusState state, List<RouteEntity> routes) {
        // Check active trip's end park first (higher priority)
        if (state.activeRouteId() != null) {
            RouteEntity active = findActiveRoute(state, routes);
            if (active != null && active.getEndBusPark() != null
                    && active.getEndBusPark().getPolygon() != null) {
                try {
                    if (geofenceService.contains(active.getEndBusPark().getPolygon(), lat, lon)) {
                        return active.getEndBusPark().getId();
                    }
                } catch (Exception ignored) {}
            }
        }
        for (RouteEntity r : routes) {
            try {
                if (r.getStartBusPark() != null && r.getStartBusPark().getPolygon() != null
                        && geofenceService.contains(r.getStartBusPark().getPolygon(), lat, lon)) {
                    return r.getStartBusPark().getId();
                }
                if (r.getEndBusPark() != null && r.getEndBusPark().getPolygon() != null
                        && geofenceService.contains(r.getEndBusPark().getPolygon(), lat, lon)) {
                    return r.getEndBusPark().getId();
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    // ── Snapshot builders ─────────────────────────────────────────────────────

    private VehicleLiveSnapshot buildSnapshot(BusEntity bus, RouteEntity activeRoute,
            List<RouteEntity> routes, BusState state, VehiclePayload.GpsData gps,
            StopResolverService.StopResolution stopRes, int onBoard, Instant timestamp) {

        UUID   routeId   = activeRoute != null ? activeRoute.getId()   : fallbackRouteId(routes);
        String routeCode = routeCode(activeRoute, routes);
        String routeName = activeRoute != null ? activeRoute.getName() : fallbackRouteName(routes);
        Integer avail    = bus.getCapacity() != null ? Math.max(0, bus.getCapacity() - onBoard) : null;

        return new VehicleLiveSnapshot(
                bus.getId(), bus.getPlateNumber(),
                routeId, routeCode, routeName,
                parseDouble(gps.getLatitude()), parseDouble(gps.getLongitude()),
                parseDouble(gps.getSpeedKmh()), parseDouble(gps.getHeadingDeg()),
                true, false,
                stopRes.currentStopName(), stopRes.nextStopName(),
                onBoard, avail, state.activeTripId(), timestamp);
    }

    private VehicleLiveSnapshot buildNoFixSnapshot(BusEntity bus, List<RouteEntity> routes,
            BusState state, Instant timestamp) {
        VehicleLiveSnapshot prev = state.lastSnapshot();
        return new VehicleLiveSnapshot(
                bus.getId(), bus.getPlateNumber(),
                fallbackRouteId(routes), routeCode(null, routes), fallbackRouteName(routes),
                prev != null ? prev.latitude()           : null,
                prev != null ? prev.longitude()          : null,
                null, null, false, false,
                prev != null ? prev.currentStopName()    : null,
                prev != null ? prev.nextStopName()       : null,
                prev != null ? prev.passengersOnBoard()  : 0,
                prev != null ? prev.availableSeats()     : bus.getCapacity(),
                state.activeTripId(), timestamp);
    }

    // ── Misc helpers ──────────────────────────────────────────────────────────

    private BusState syncFromDb(BusEntity bus, BusState state) {
        if (state.activeTripId() == null) {
            TripEntity active = tripRepository.findByBusIdAndStatus(bus.getId(), TripStatus.ACTIVE)
                    .orElse(null);
            if (active != null) {
                state = state.withTrip(active.getId(), active.getRoute().getId(),
                        active.getSnapshotIn(), active.getSnapshotOut());
            }
        }
        if (state.lastCompletedRouteId() == null && bus.getLastCompletedRouteId() != null) {
            state = new BusState(state.activeTripId(), state.activeRouteId(),
                    bus.getLastCompletedRouteId(), state.inBusParkId(),
                    state.snapshotIn(), state.snapshotOut(),
                    state.lastPassedStopSeq(), state.lastSeenAt(), state.lastSnapshot());
        }
        return state;
    }

    private RouteEntity findActiveRoute(BusState state, List<RouteEntity> routes) {
        if (state.activeRouteId() == null) return null;
        return routes.stream().filter(r -> r.getId().equals(state.activeRouteId())).findFirst()
                .orElseGet(() -> routeRepository.findById(state.activeRouteId()).orElse(null));
    }

    private int calculateOnBoard(BusState state, VehiclePayload.Passengers pax) {
        if (state.activeTripId() == null || pax == null) return 0;
        int inDelta  = Math.max(0, pax.getIn()  - state.snapshotIn());
        int outDelta = Math.max(0, pax.getOut() - state.snapshotOut());
        return Math.max(0, inDelta - outDelta);
    }

    private UUID   fallbackRouteId(List<RouteEntity> routes)   { return routes.isEmpty() ? null : routes.get(0).getId(); }
    private String fallbackRouteName(List<RouteEntity> routes) { return routes.isEmpty() ? null : routes.get(0).getName(); }
    private String routeCode(RouteEntity active, List<RouteEntity> routes) {
        RouteEntity r = active != null ? active : (routes.isEmpty() ? null : routes.get(0));
        return r != null && r.getRouteCode() != null ? r.getRouteCode().getCode() : null;
    }

    private Double parseDouble(String v) {
        if (v == null || v.isBlank() || "null".equalsIgnoreCase(v)) return null;
        try { return Double.parseDouble(v); } catch (Exception e) { return null; }
    }

    private Instant parseInstant(String ts) {
        if (ts == null) return Instant.now();
        try { return Instant.parse(ts); } catch (Exception e) {
            try { return ZonedDateTime.parse(ts, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant(); }
            catch (Exception e2) { return Instant.now(); }
        }
    }

    // ── In-memory bus state ───────────────────────────────────────────────────

    public record BusState(
            UUID              activeTripId,
            UUID              activeRouteId,
            UUID              lastCompletedRouteId,
            UUID              inBusParkId,
            int               snapshotIn,
            int               snapshotOut,
            int               lastPassedStopSeq,
            Instant           lastSeenAt,
            VehicleLiveSnapshot lastSnapshot
    ) {
        static BusState empty() {
            return new BusState(null, null, null, null, 0, 0, 0, null, null);
        }

        BusState withGeofence(UUID parkId) {
            return new BusState(activeTripId, activeRouteId, lastCompletedRouteId,
                    parkId, snapshotIn, snapshotOut, lastPassedStopSeq, lastSeenAt, lastSnapshot);
        }

        BusState withTrip(UUID tripId, UUID routeId, int snapIn, int snapOut) {
            return new BusState(tripId, routeId, lastCompletedRouteId,
                    inBusParkId, snapIn, snapOut, lastPassedStopSeq, lastSeenAt, lastSnapshot);
        }

        BusState withTripCleared(UUID completedRouteId) {
            return new BusState(null, null, completedRouteId,
                    inBusParkId, 0, 0, 0, lastSeenAt, lastSnapshot);
        }

        BusState withStop(int passedSeq) {
            return new BusState(activeTripId, activeRouteId, lastCompletedRouteId,
                    inBusParkId, snapshotIn, snapshotOut, passedSeq, lastSeenAt, lastSnapshot);
        }

        BusState withSeen(Instant t, VehicleLiveSnapshot snap) {
            return new BusState(activeTripId, activeRouteId, lastCompletedRouteId,
                    inBusParkId, snapshotIn, snapshotOut, lastPassedStopSeq, t, snap);
        }
    }
}
