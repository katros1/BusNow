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
        // Compute progress early so handleTripCompletion can use it as a fallback
        // when the terminal geofence fails (e.g. swapped polygon coordinates in DB).
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

    /** Called by StalenessChecker so reconnecting clients also see the stale flag. */
    public void markStale(UUID busId, VehicleLiveSnapshot staleSnap) {
        busStates.computeIfPresent(busId, (id, state) -> state.withSeen(state.lastSeenAt(), staleSnap));
    }

    // ── Trip lifecycle helpers ────────────────────────────────────────────────

    private BusState handleTripCompletion(BusState state, UUID nowInParkId, Double progressPercent,
            VehiclePayload.Passengers pax, BusEntity bus, List<RouteEntity> routes) {
        if (state.activeTripId() == null) return state;

        RouteEntity activeRoute = findActiveRoute(state, routes);
        if (activeRoute == null || activeRoute.getEndBusPark() == null) return state;

        // Primary: bus entered the end-park geofence
        boolean atEndPark = nowInParkId != null
                && nowInParkId.equals(activeRoute.getEndBusPark().getId());
        // Fallback: route progress >= 99 % (geofence unreliable due to swapped polygon coords)
        boolean progressDone = progressPercent != null && progressPercent >= 99.0;

        if (!atEndPark && !progressDone) return state;

        int finalIn  = pax != null ? pax.getIn()  : state.snapshotIn();
        int finalOut = pax != null ? pax.getOut() : state.snapshotOut();
        tripService.completeTrip(state.activeTripId(), finalIn, finalOut);
        log.info("Trip {} completed — bus {} at terminal (geofence={}, progress={}) ",
                state.activeTripId(), bus.getPlateNumber(), atEndPark,
                progressPercent != null ? String.format("%.1f%%", progressPercent) : "n/a");
        return state.withTripCleared(activeRoute.getId());
    }

    /**
     * Starts a trip for the first route whose start terminal the bus is near.
     *
     * Detection uses progressPercent <= START_THRESHOLD_PCT on the route geometry —
     * the same geometry that drives the 99 % completion trigger.  This is more robust
     * than a fixed-radius proximity check on pts[0] because:
     *   • it tolerates the route LineString starting a few hundred metres before the
     *     physical terminal building (common in Kigali road data), and
     *   • it works correctly regardless of whether the LineString is oriented
     *     start→end or end→start (the 0 % end is always the start terminal).
     *
     * 5 % ≈ 750 m on a 15 km route — generous enough to cover any terminal offset
     * while still far from the midpoint of the route.
     */
    private static final double START_THRESHOLD_PCT = 5.0;

    private BusState handleTripStart(BusState state, double lat, double lon,
            VehiclePayload.Passengers pax, BusEntity bus, List<RouteEntity> routes) {
        if (state.activeTripId() != null || routes.isEmpty()) return state;

        for (RouteEntity r : routes) {
            if (r.getGeo() == null) continue;
            double progress = GeoUtils.progressPercent(r.getGeo(), lat, lon);
            if (progress > START_THRESHOLD_PCT) continue;

            int snapIn  = pax != null ? pax.getIn()  : 0;
            int snapOut = pax != null ? pax.getOut() : 0;
            try {
                TripEntity trip = tripService.startTrip(bus, r, snapIn, snapOut, 0);
                log.info("Trip {} started — bus {} at start of route {} (progress={})%",
                        trip.getId(), bus.getPlateNumber(), r.getName(),
                        String.format("%.1f", progress));
                return state.withTrip(trip.getId(), r.getId(), snapIn, snapOut);
            } catch (Exception e) {
                log.error("Failed to start trip for bus {}: {}", bus.getPlateNumber(), e.getMessage());
            }
        }
        return state;
    }

    // ── Geofence helper ───────────────────────────────────────────────────────

    /**
     * Threshold used for route-endpoint proximity fallback.
     * A GPS fix within this radius of a route's first/last vertex is considered
     * "at the bus park" even when the polygon geofence fails (e.g. swapped coordinates in DB).
     */
    private static final double PARK_RADIUS_M = 250.0;

    private UUID parkGeofenceCheck(double lat, double lon, BusState state, List<RouteEntity> routes) {
        // ── Primary: polygon containment (works when DB coordinates are correct) ──
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

        // ── Fallback: proximity to route LineString endpoints ─────────────────────
        // Used when polygon geofence fails (e.g. coordinates stored in wrong order).
        // The route geometry IS always correct — its first vertex maps to startBusPark
        // and its last vertex maps to endBusPark.
        for (RouteEntity r : routes) {
            if (r.getGeo() == null) continue;
            org.locationtech.jts.geom.Coordinate[] pts = r.getGeo().getCoordinates();
            if (pts.length < 2) continue;

            if (r.getStartBusPark() != null) {
                double sLat = pts[0].y, sLon = pts[0].x;
                if (GeoUtils.haversineM(lat, lon, sLat, sLon) <= PARK_RADIUS_M) {
                    return r.getStartBusPark().getId();
                }
            }
            if (r.getEndBusPark() != null) {
                double eLat = pts[pts.length - 1].y, eLon = pts[pts.length - 1].x;
                if (GeoUtils.haversineM(lat, lon, eLat, eLon) <= PARK_RADIUS_M) {
                    return r.getEndBusPark().getId();
                }
            }
        }
        return null;
    }

    // ── Snapshot builders ─────────────────────────────────────────────────────

    private VehicleLiveSnapshot buildSnapshot(BusEntity bus, RouteEntity activeRoute,
            List<RouteEntity> routes, BusState state, VehiclePayload.GpsData gps,
            StopResolverService.StopResolution stopRes, int onBoard, Instant timestamp) {

        // When no active trip: show null routeId/routeName so the frontend displays
        // "No route assigned" and tripId=null. The bus will receive new IDs the moment
        // it departs the terminal and a fresh trip starts.
        // routeCode is still resolved (for the header chip) even without an active trip.
        RouteEntity displayRoute = activeRoute;

        UUID    routeId   = activeRoute != null ? activeRoute.getId()   : null;
        String  routeCode = routeCodeFor(activeRoute, routes);
        String  routeName = activeRoute != null ? activeRoute.getName() : null;
        Integer avail     = bus.getCapacity() != null ? Math.max(0, bus.getCapacity() - onBoard) : null;

        double lat = parseDouble(gps.getLatitude());
        double lon = parseDouble(gps.getLongitude());

        Double distToNextStop = null;
        Double distToTerminal = null;
        Double progressPct    = null;

        if (displayRoute != null && displayRoute.getGeo() != null) {
            var line = displayRoute.getGeo();

            if (stopRes.nextStopLat() != null && stopRes.nextStopLon() != null) {
                distToNextStop = GeoUtils.distanceAlongLineM(
                        line, lat, lon, stopRes.nextStopLat(), stopRes.nextStopLon());
            }

            // Distance to terminal only while actively on a trip.
            // Use the route's last vertex as terminal position — this avoids relying on
            // bus park polygon centroids, which may have swapped lat/lon in the DB.
            if (activeRoute != null) {
                org.locationtech.jts.geom.Coordinate[] pts = line.getCoordinates();
                if (pts.length > 0) {
                    org.locationtech.jts.geom.Coordinate end = pts[pts.length - 1];
                    distToTerminal = GeoUtils.distanceAlongLineM(line, lat, lon, end.y, end.x);
                }
            }

            // Always compute progress so the bus icon stays visible on the route line
            progressPct = GeoUtils.progressPercent(line, lat, lon);
        }

        // When all stops are passed nextStopName is null — fall back to the terminal name
        // so the UI always shows a destination rather than blank.
        String nextStopName = stopRes.nextStopName();
        if (nextStopName == null && activeRoute != null
                && activeRoute.getEndBusPark() != null) {
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
                distToNextStop, distToTerminal, progressPct,
                onBoard, avail, state.activeTripId(), timestamp);
    }

    private VehicleLiveSnapshot buildNoFixSnapshot(BusEntity bus, List<RouteEntity> routes,
            BusState state, Instant timestamp) {
        VehicleLiveSnapshot prev = state.lastSnapshot();
        // No active trip → routeId/routeName null (consistent with buildSnapshot)
        UUID   routeId   = state.activeTripId() != null && prev != null ? prev.routeId()   : null;
        String routeCode = routeCodeFor(null, routes);
        String routeName = state.activeTripId() != null && prev != null ? prev.routeName() : null;
        return new VehicleLiveSnapshot(
                bus.getId(), bus.getPlateNumber(),
                routeId, routeCode, routeName,
                prev != null ? prev.latitude()            : null,
                prev != null ? prev.longitude()           : null,
                null, null, false, false,
                prev != null ? prev.currentStopName()     : null,
                prev != null ? prev.nextStopName()        : null,
                prev != null ? prev.distanceToNextStopM() : null,
                prev != null ? prev.distanceToTerminalM() : null,
                prev != null ? prev.progressPercent()     : null,
                prev != null ? prev.passengersOnBoard()   : 0,
                prev != null ? prev.availableSeats()      : bus.getCapacity(),
                state.activeTripId(), timestamp);
    }

    // ── Misc helpers ──────────────────────────────────────────────────────────

    private BusState syncFromDb(BusEntity bus, BusState state) {
        if (state.activeTripId() == null) {
            TripEntity active = tripRepository.findFirstByBusIdAndStatusOrderByStartedAtDesc(bus.getId(), TripStatus.ACTIVE)
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
