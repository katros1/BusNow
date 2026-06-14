package ba.backend.tracking.service;

import ba.backend.bus.entity.BusEntity;
import ba.backend.bus.repository.BusRepository;
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
 * Route assignment / trip lifecycle:
 *   1. Bus inside startBusPark geofence → pendingRouteId set (route shown in UI, no trip yet)
 *   2. Bus exits park while pendingRouteId is set → trip starts
 *   3. Bus enters endBusPark → trip completes → route whose startBusPark = endBusPark is set as
 *      new pendingRouteId (ping-pong toggle continues automatically)
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
                            trip.getSnapshotIn(), trip.getSnapshotOut(), trip.getStartedAt());
            UUID lastRoute = trip.getBus().getLastCompletedRouteId();
            if (lastRoute != null) {
                state = new BusState(state.activeTripId(), state.activeRouteId(), lastRoute,
                        null, null, state.snapshotIn(), state.snapshotOut(), 0,
                        Double.MAX_VALUE, trip.getStartedAt(), null, null);
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

        VehiclePayload.GpsData    gps        = payload.getGps();
        VehiclePayload.Passengers pax        = payload.getPassengers();
        Instant                   recordedAt = parseInstant(payload.getDevice().getTimestamp());

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
        Double earlyProgress = null;
        if (state.activeTripId() != null) {
            RouteEntity ar = findActiveRoute(state, routes);
            if (ar != null && ar.getGeo() != null) {
                earlyProgress = GeoUtils.progressPercent(ar.getGeo(), lat, lon);
            }
        }

        UUID nowInParkId = parkGeofenceCheck(lat, lon, state, routes);
        state = state.withGeofence(nowInParkId);
        state = handleTripCompletion(state, nowInParkId, earlyProgress, pax, bus, routes);
        state = handleTripStart(state, lat, lon, pax, bus, routes);

        // ── Passengers ───────────────────────────────────────────────────────
        int onBoard = calculateOnBoard(state, pax);
        if (state.activeTripId() != null) {
            tripService.updatePassengersOnBoard(state.activeTripId(), onBoard);
        }

        // ── Stop detection ────────────────────────────────────────────────────
        RouteEntity activeRoute = findActiveRoute(state, routes);
        StopResolverService.StopResolution stopRes = activeRoute != null
                ? stopResolver.resolve(activeRoute.getRouteStops(), activeRoute.getGeo(), lat, lon,
                        state.lastPassedStopSeq(), state.minDistToNextStopM())
                : StopResolverService.StopResolution.none(state.lastPassedStopSeq(), state.minDistToNextStopM());
        state = state.withStop(stopRes.updatedLastPassedSequence(), stopRes.updatedMinDistToNextStopM());

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

    public void markStale(UUID busId, VehicleLiveSnapshot staleSnap) {
        busStates.computeIfPresent(busId, (id, state) -> state.withSeen(state.lastSeenAt(), staleSnap));
    }

    // ── Trip lifecycle helpers ────────────────────────────────────────────────

    private BusState handleTripCompletion(BusState state, UUID nowInParkId, Double progressPercent,
            VehiclePayload.Passengers pax, BusEntity bus, List<RouteEntity> routes) {
        if (state.activeTripId() == null) return state;

        RouteEntity activeRoute = findActiveRoute(state, routes);
        if (activeRoute == null || activeRoute.getEndBusPark() == null) return state;

        boolean atEndPark    = nowInParkId != null && nowInParkId.equals(activeRoute.getEndBusPark().getId());
        boolean progressDone = progressPercent != null && progressPercent >= 99.0;
        if (!atEndPark && !progressDone) return state;

        int finalIn  = pax != null ? pax.getIn()  : state.snapshotIn();
        int finalOut = pax != null ? pax.getOut() : state.snapshotOut();
        tripService.completeTrip(state.activeTripId(), finalIn, finalOut);
        log.info("Trip {} completed — bus {} at terminal (geofence={}, progress={})",
                state.activeTripId(), bus.getPlateNumber(), atEndPark,
                progressPercent != null ? String.format("%.1f%%", progressPercent) : "n/a");

        // Find the return route: its startBusPark = current endBusPark → ping-pong toggle
        UUID endParkId = activeRoute.getEndBusPark().getId();
        RouteEntity returnRoute = routes.stream()
                .filter(r -> r.getStartBusPark() != null
                        && r.getStartBusPark().getId().equals(endParkId)
                        && !r.getId().equals(activeRoute.getId()))
                .findFirst().orElse(null);

        if (returnRoute != null) {
            log.info("Auto-assigning return route '{}' for bus {}", returnRoute.getName(), bus.getPlateNumber());
            return state.withTripClearedAndPending(activeRoute.getId(), returnRoute.getId());
        }
        return state.withTripCleared(activeRoute.getId());
    }

    /**
     * Route assignment and trip start.
     *
     * Phase A — bus near/inside a startBusPark:
     *   Set pendingRouteId so the UI shows the upcoming route while the bus is parked.
     *   No trip is started yet.
     *
     * Phase B — bus has a pendingRoute and has moved away from its startBusPark:
     *   Bus has departed → start the trip now.
     *
     * Detection uses three layers in order:
     *   1. Polygon containment (precise, fails when coordinates are swapped in DB)
     *   2. Proximity to polygon centroid (robust fallback — tolerates swapped lon/lat)
     *   3. Proximity to route LineString start point (works even with no bus-park polygon)
     */
    private BusState handleTripStart(BusState state, double lat, double lon,
            VehiclePayload.Passengers pax, BusEntity bus, List<RouteEntity> routes) {
        if (state.activeTripId() != null || routes.isEmpty()) return state;

        log.debug("handleTripStart — bus={} lat={} lon={} routes={} pendingRoute={}",
                bus.getPlateNumber(), lat, lon, routes.size(), state.pendingRouteId());

        // Phase A: detect which startBusPark (if any) the bus is currently at
        for (RouteEntity r : routes) {
            if (r.getStartBusPark() == null) {
                log.warn("Route '{}' has no startBusPark — skipping", r.getName());
                continue;
            }
            boolean atStart = isNearPark(r.getStartBusPark().getPolygon(), r.getGeo(), true, lat, lon);
            log.debug("Route '{}' startBusPark '{}' near-check ({},{}) = {}",
                    r.getName(), r.getStartBusPark().getName(), lat, lon, atStart);
            if (atStart) {
                if (r.getId().equals(state.pendingRouteId())) return state; // still parked, no change
                log.info("Bus {} at start park '{}' → pending route '{}'",
                        bus.getPlateNumber(), r.getStartBusPark().getName(), r.getName());
                return state.withPendingRoute(r.getId());
            }
        }

        // Phase B: no route's startBusPark matches — if a pending route is set, the bus has departed
        if (state.pendingRouteId() == null) return state;

        RouteEntity pendingRoute = routes.stream()
                .filter(r -> r.getId().equals(state.pendingRouteId()))
                .findFirst()
                .orElseGet(() -> routeRepository.findById(state.pendingRouteId()).orElse(null));

        if (pendingRoute == null) return state;

        // Safety: if bus is somehow still at the start park, don't start yet
        if (pendingRoute.getStartBusPark() != null
                && isNearPark(pendingRoute.getStartBusPark().getPolygon(),
                              pendingRoute.getGeo(), true, lat, lon)) {
            return state;
        }

        int snapIn  = pax != null ? pax.getIn()  : 0;
        int snapOut = pax != null ? pax.getOut() : 0;
        try {
            TripEntity trip = tripService.startTrip(bus, pendingRoute, snapIn, snapOut, 0);
            log.info("Trip {} started — bus {} departed '{}' → route '{}'",
                    trip.getId(), bus.getPlateNumber(),
                    pendingRoute.getStartBusPark() != null ? pendingRoute.getStartBusPark().getName() : "?",
                    pendingRoute.getName());
            return state.withTrip(trip.getId(), pendingRoute.getId(), snapIn, snapOut, trip.getStartedAt());
        } catch (Exception e) {
            log.error("Failed to start trip for bus {}: {}", bus.getPlateNumber(), e.getMessage());
        }
        return state;
    }

    /**
     * Returns true if (lat, lon) is inside or within PARK_RADIUS_M of a bus park.
     *
     * Tries three strategies in order:
     *   1. Polygon containment
     *   2. Distance to polygon centroid (handles swapped lat/lon in PostGIS)
     *   3. Distance to the first (useStart=true) or last (false) point of the route LineString
     *
     * @param polygon   bus-park polygon (may be null)
     * @param routeGeo  route LineString (may be null)
     * @param useStart  true → use first point of route; false → use last point
     */
    private boolean isNearPark(org.locationtech.jts.geom.Polygon polygon,
                               org.locationtech.jts.geom.LineString routeGeo,
                               boolean useStart, double lat, double lon) {
        // 1. Polygon containment
        if (polygon != null) {
            try {
                if (geofenceService.contains(polygon, lat, lon)) return true;
            } catch (Exception ignored) {}

            // 2. Centroid proximity — tolerates swapped coordinates stored in DB
            try {
                org.locationtech.jts.geom.Point c = polygon.getCentroid();
                double rawY = c.getY(), rawX = c.getX();
                double cLat, cLon;
                // Heuristic: if Y looks like a large longitude (> 10°) and X like a small latitude (< 5°),
                // the ring was stored with lon/lat swapped.
                if (Math.abs(rawY) > 10 && Math.abs(rawX) < 5) {
                    cLat = rawX; cLon = rawY;
                } else {
                    cLat = rawY; cLon = rawX;
                }
                if (GeoUtils.haversineM(lat, lon, cLat, cLon) <= PARK_RADIUS_M) return true;
            } catch (Exception ignored) {}
        }

        // 3. Route LineString endpoint proximity
        if (routeGeo != null && routeGeo.getNumPoints() > 0) {
            try {
                org.locationtech.jts.geom.Coordinate[] pts = routeGeo.getCoordinates();
                org.locationtech.jts.geom.Coordinate pt = useStart ? pts[0] : pts[pts.length - 1];
                // JTS stores (lon, lat) in X/Y — but check both orderings
                double ptLat = pt.y, ptLon = pt.x;
                if (GeoUtils.haversineM(lat, lon, ptLat, ptLon) <= PARK_RADIUS_M) return true;
                // Also try swapped (in case stored as lat, lon)
                if (Math.abs(pt.y) > 10 && Math.abs(pt.x) < 5) {
                    if (GeoUtils.haversineM(lat, lon, pt.x, pt.y) <= PARK_RADIUS_M) return true;
                }
            } catch (Exception ignored) {}
        }

        return false;
    }

    // ── Geofence helper ───────────────────────────────────────────────────────

    private static final double PARK_RADIUS_M = 250.0;

    private UUID parkGeofenceCheck(double lat, double lon, BusState state, List<RouteEntity> routes) {
        // Check active route's end park first (most likely match during an active trip)
        if (state.activeRouteId() != null) {
            RouteEntity active = findActiveRoute(state, routes);
            if (active != null && active.getEndBusPark() != null
                    && isNearPark(active.getEndBusPark().getPolygon(), active.getGeo(), false, lat, lon)) {
                return active.getEndBusPark().getId();
            }
        }
        // Then check all route start/end parks
        for (RouteEntity r : routes) {
            if (r.getStartBusPark() != null
                    && isNearPark(r.getStartBusPark().getPolygon(), r.getGeo(), true, lat, lon)) {
                return r.getStartBusPark().getId();
            }
            if (r.getEndBusPark() != null
                    && isNearPark(r.getEndBusPark().getPolygon(), r.getGeo(), false, lat, lon)) {
                return r.getEndBusPark().getId();
            }
        }
        return null;
    }

    // ── Snapshot builders ─────────────────────────────────────────────────────

    private VehicleLiveSnapshot buildSnapshot(BusEntity bus, RouteEntity activeRoute,
            List<RouteEntity> routes, BusState state, VehiclePayload.GpsData gps,
            StopResolverService.StopResolution stopRes, int onBoard, Instant timestamp) {

        // When no active trip, show the pending route so the UI displays the upcoming assignment.
        RouteEntity displayRoute = activeRoute != null ? activeRoute : findPendingRoute(state, routes);

        UUID    routeId   = displayRoute != null ? displayRoute.getId()   : null;
        String  routeCode = routeCodeFor(displayRoute, routes);
        String  routeName = displayRoute != null ? displayRoute.getName() : null;
        Integer avail     = bus.getCapacity() != null ? Math.max(0, bus.getCapacity() - onBoard) : null;

        double lat = parseDouble(gps.getLatitude());
        double lon = parseDouble(gps.getLongitude());

        Double distToNextStop = null;
        Double distToTerminal = null;
        Double progressPct    = null;

        // Straight-line distance to next stop — works for both on-route and off-route buses.
        if (stopRes.nextStopLat() != null && stopRes.nextStopLon() != null) {
            distToNextStop = GeoUtils.haversineM(lat, lon, stopRes.nextStopLat(), stopRes.nextStopLon());
        }

        if (displayRoute != null && displayRoute.getGeo() != null) {
            var line = displayRoute.getGeo();

            if (activeRoute != null) {
                org.locationtech.jts.geom.Coordinate[] pts = line.getCoordinates();
                if (pts.length > 0) {
                    org.locationtech.jts.geom.Coordinate end = pts[pts.length - 1];
                    distToTerminal = GeoUtils.distanceAlongLineM(line, lat, lon, end.y, end.x);
                }
            }

            progressPct = GeoUtils.progressPercent(line, lat, lon);
        }

        String nextStopName = stopRes.nextStopName();
        Double nextStopLat  = stopRes.nextStopLat();
        Double nextStopLon  = stopRes.nextStopLon();
        if (nextStopName == null && activeRoute != null && activeRoute.getEndBusPark() != null) {
            nextStopName = activeRoute.getEndBusPark().getName();
            if (distToTerminal != null) distToNextStop = distToTerminal;
        }

        return new VehicleLiveSnapshot(
                bus.getId(), bus.getPlateNumber(),
                routeId, routeCode, routeName,
                lat, lon,
                parseDouble(gps.getSpeedKmh()), parseDouble(gps.getHeadingDeg()),
                true, false,
                stopRes.currentStopName(), nextStopName,
                nextStopLat, nextStopLon,
                distToNextStop, distToTerminal, progressPct,
                state.lastPassedStopSeq(),
                onBoard, avail, state.activeTripId(), state.tripStartedAt(), timestamp);
    }

    private VehicleLiveSnapshot buildNoFixSnapshot(BusEntity bus, List<RouteEntity> routes,
            BusState state, Instant timestamp) {
        VehicleLiveSnapshot prev = state.lastSnapshot();
        RouteEntity displayRoute = findPendingRoute(state, routes);
        UUID   routeId   = state.activeTripId() != null && prev != null ? prev.routeId()
                : (displayRoute != null ? displayRoute.getId() : null);
        String routeCode = routeCodeFor(displayRoute, routes);
        String routeName = state.activeTripId() != null && prev != null ? prev.routeName()
                : (displayRoute != null ? displayRoute.getName() : null);
        return new VehicleLiveSnapshot(
                bus.getId(), bus.getPlateNumber(),
                routeId, routeCode, routeName,
                prev != null ? prev.latitude()            : null,
                prev != null ? prev.longitude()           : null,
                null, null, false, false,
                prev != null ? prev.currentStopName()     : null,
                prev != null ? prev.nextStopName()        : null,
                prev != null ? prev.nextStopLat()         : null,
                prev != null ? prev.nextStopLon()         : null,
                prev != null ? prev.distanceToNextStopM() : null,
                prev != null ? prev.distanceToTerminalM() : null,
                prev != null ? prev.progressPercent()     : null,
                state.lastPassedStopSeq(),
                prev != null ? prev.passengersOnBoard()   : 0,
                prev != null ? prev.availableSeats()      : bus.getCapacity(),
                state.activeTripId(), state.tripStartedAt(), timestamp);
    }

    // ── Misc helpers ──────────────────────────────────────────────────────────

    private BusState syncFromDb(BusEntity bus, BusState state) {
        if (state.activeTripId() == null) {
            TripEntity active = tripRepository
                    .findFirstByBusIdAndStatusOrderByStartedAtDesc(bus.getId(), TripStatus.ACTIVE)
                    .orElse(null);
            if (active != null) {
                state = state.withTrip(active.getId(), active.getRoute().getId(),
                        active.getSnapshotIn(), active.getSnapshotOut(), active.getStartedAt());
            }
        }
        if (state.lastCompletedRouteId() == null && bus.getLastCompletedRouteId() != null) {
            state = new BusState(state.activeTripId(), state.activeRouteId(),
                    bus.getLastCompletedRouteId(), state.pendingRouteId(), state.inBusParkId(),
                    state.snapshotIn(), state.snapshotOut(),
                    state.lastPassedStopSeq(), state.minDistToNextStopM(),
                    state.tripStartedAt(), state.lastSeenAt(), state.lastSnapshot());
        }
        return state;
    }

    private RouteEntity findActiveRoute(BusState state, List<RouteEntity> routes) {
        if (state.activeRouteId() == null) return null;
        return routes.stream().filter(r -> r.getId().equals(state.activeRouteId())).findFirst()
                .orElseGet(() -> routeRepository.findById(state.activeRouteId()).orElse(null));
    }

    private RouteEntity findPendingRoute(BusState state, List<RouteEntity> routes) {
        if (state.pendingRouteId() == null) return null;
        return routes.stream().filter(r -> r.getId().equals(state.pendingRouteId())).findFirst()
                .orElseGet(() -> routeRepository.findById(state.pendingRouteId()).orElse(null));
    }

    private int calculateOnBoard(BusState state, VehiclePayload.Passengers pax) {
        if (state.activeTripId() == null || pax == null) return 0;
        int inDelta  = Math.max(0, pax.getIn()  - state.snapshotIn());
        int outDelta = Math.max(0, pax.getOut() - state.snapshotOut());
        return Math.max(0, inDelta - outDelta);
    }

    private String routeCodeFor(RouteEntity route, List<RouteEntity> routes) {
        RouteEntity r = route != null ? route : (routes.isEmpty() ? null : routes.get(0));
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
            UUID                activeTripId,
            UUID                activeRouteId,
            UUID                lastCompletedRouteId,
            UUID                pendingRouteId,
            UUID                inBusParkId,
            int                 snapshotIn,
            int                 snapshotOut,
            int                 lastPassedStopSeq,
            /** Minimum straight-line distance (m) ever observed from bus to the current next stop.
             *  Reset to MAX_VALUE when lastPassedStopSeq advances (new next stop). */
            double              minDistToNextStopM,
            Instant             tripStartedAt,
            Instant             lastSeenAt,
            VehicleLiveSnapshot lastSnapshot
    ) {
        static BusState empty() {
            return new BusState(null, null, null, null, null, 0, 0, 0,
                    Double.MAX_VALUE, null, null, null);
        }

        BusState withGeofence(UUID parkId) {
            return new BusState(activeTripId, activeRouteId, lastCompletedRouteId,
                    pendingRouteId, parkId, snapshotIn, snapshotOut, lastPassedStopSeq,
                    minDistToNextStopM, tripStartedAt, lastSeenAt, lastSnapshot);
        }

        BusState withPendingRoute(UUID routeId) {
            return new BusState(activeTripId, activeRouteId, lastCompletedRouteId,
                    routeId, inBusParkId, snapshotIn, snapshotOut, lastPassedStopSeq,
                    minDistToNextStopM, tripStartedAt, lastSeenAt, lastSnapshot);
        }

        BusState withTrip(UUID tripId, UUID routeId, int snapIn, int snapOut, Instant startedAt) {
            return new BusState(tripId, routeId, lastCompletedRouteId,
                    null, inBusParkId, snapIn, snapOut, 0,
                    Double.MAX_VALUE, startedAt, lastSeenAt, lastSnapshot);
        }

        BusState withTripCleared(UUID completedRouteId) {
            return new BusState(null, null, completedRouteId,
                    null, inBusParkId, 0, 0, 0,
                    Double.MAX_VALUE, null, lastSeenAt, lastSnapshot);
        }

        BusState withTripClearedAndPending(UUID completedRouteId, UUID newPendingRouteId) {
            return new BusState(null, null, completedRouteId,
                    newPendingRouteId, inBusParkId, 0, 0, 0,
                    Double.MAX_VALUE, null, lastSeenAt, lastSnapshot);
        }

        BusState withStop(int passedSeq, double minDist) {
            return new BusState(activeTripId, activeRouteId, lastCompletedRouteId,
                    pendingRouteId, inBusParkId, snapshotIn, snapshotOut, passedSeq,
                    minDist, tripStartedAt, lastSeenAt, lastSnapshot);
        }

        BusState withSeen(Instant t, VehicleLiveSnapshot snap) {
            return new BusState(activeTripId, activeRouteId, lastCompletedRouteId,
                    pendingRouteId, inBusParkId, snapshotIn, snapshotOut, lastPassedStopSeq,
                    minDistToNextStopM, tripStartedAt, t, snap);
        }
    }
}
