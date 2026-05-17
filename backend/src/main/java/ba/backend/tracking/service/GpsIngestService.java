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
                            trip.getSnapshotIn(), trip.getSnapshotOut());
            UUID lastRoute = trip.getBus().getLastCompletedRouteId();
            if (lastRoute != null) {
                state = new BusState(state.activeTripId(), state.activeRouteId(), lastRoute,
                        null, null, state.snapshotIn(), state.snapshotOut(), 0, null, null);
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
     * Route assignment and trip start, driven by bus park geofence.
     *
     * Phase A — bus inside a startBusPark:
     *   Set pendingRouteId for that route so the UI shows the route name while parked.
     *   No trip is started yet.
     *
     * Phase B — bus has a pendingRoute and is no longer inside its startBusPark:
     *   Bus has departed → start the trip now.
     */
    private BusState handleTripStart(BusState state, double lat, double lon,
            VehiclePayload.Passengers pax, BusEntity bus, List<RouteEntity> routes) {
        if (state.activeTripId() != null || routes.isEmpty()) return state;

        log.debug("handleTripStart — bus={} lat={} lon={} routes={} pendingRoute={}",
                bus.getPlateNumber(), lat, lon, routes.size(), state.pendingRouteId());

        // Phase A: check each route's startBusPark geofence
        for (RouteEntity r : routes) {
            if (r.getStartBusPark() == null) {
                log.warn("Route '{}' has no startBusPark — skipping geofence check", r.getName());
                continue;
            }
            if (r.getStartBusPark().getPolygon() == null) {
                log.warn("Route '{}' startBusPark '{}' has null polygon — skipping geofence check",
                        r.getName(), r.getStartBusPark().getName());
                continue;
            }
            try {
                boolean inside = geofenceService.contains(r.getStartBusPark().getPolygon(), lat, lon);
                log.debug("Route '{}' startBusPark '{}' contains ({},{}) = {}",
                        r.getName(), r.getStartBusPark().getName(), lat, lon, inside);
                if (inside) {
                    if (r.getId().equals(state.pendingRouteId())) return state; // already pending, bus still parked
                    log.info("Bus {} entered start park '{}' → pending route '{}'",
                            bus.getPlateNumber(), r.getStartBusPark().getName(), r.getName());
                    return state.withPendingRoute(r.getId());
                }
            } catch (Exception e) {
                log.error("Geofence check failed for route '{}' startBusPark '{}': {}",
                        r.getName(), r.getStartBusPark().getName(), e.getMessage(), e);
            }
        }

        // Phase B: bus is outside all startBusPark geofences — if pending route set, depart and start trip
        if (state.pendingRouteId() == null) return state;

        RouteEntity pendingRoute = routes.stream()
                .filter(r -> r.getId().equals(state.pendingRouteId()))
                .findFirst()
                .orElseGet(() -> routeRepository.findById(state.pendingRouteId()).orElse(null));

        if (pendingRoute == null) return state;

        int snapIn  = pax != null ? pax.getIn()  : 0;
        int snapOut = pax != null ? pax.getOut() : 0;
        try {
            TripEntity trip = tripService.startTrip(bus, pendingRoute, snapIn, snapOut, 0);
            log.info("Trip {} started — bus {} departed from '{}' → route '{}'",
                    trip.getId(), bus.getPlateNumber(),
                    pendingRoute.getStartBusPark() != null ? pendingRoute.getStartBusPark().getName() : "?",
                    pendingRoute.getName());
            return state.withTrip(trip.getId(), pendingRoute.getId(), snapIn, snapOut);
        } catch (Exception e) {
            log.error("Failed to start trip for bus {}: {}", bus.getPlateNumber(), e.getMessage());
        }
        return state;
    }

    // ── Geofence helper ───────────────────────────────────────────────────────

    private static final double PARK_RADIUS_M = 250.0;

    private UUID parkGeofenceCheck(double lat, double lon, BusState state, List<RouteEntity> routes) {
        // Primary: polygon containment — check active end park first
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

        // Fallback: proximity to route LineString endpoints (handles swapped polygon coords)
        for (RouteEntity r : routes) {
            if (r.getGeo() == null) continue;
            org.locationtech.jts.geom.Coordinate[] pts = r.getGeo().getCoordinates();
            if (pts.length < 2) continue;
            if (r.getStartBusPark() != null) {
                if (GeoUtils.haversineM(lat, lon, pts[0].y, pts[0].x) <= PARK_RADIUS_M) {
                    return r.getStartBusPark().getId();
                }
            }
            if (r.getEndBusPark() != null) {
                if (GeoUtils.haversineM(lat, lon, pts[pts.length - 1].y, pts[pts.length - 1].x) <= PARK_RADIUS_M) {
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

        if (displayRoute != null && displayRoute.getGeo() != null) {
            var line = displayRoute.getGeo();

            if (stopRes.nextStopLat() != null && stopRes.nextStopLon() != null) {
                distToNextStop = GeoUtils.distanceAlongLineM(
                        line, lat, lon, stopRes.nextStopLat(), stopRes.nextStopLon());
            }

            // Distance to terminal and progress only meaningful on an active trip
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
                distToNextStop, distToTerminal, progressPct,
                onBoard, avail, state.activeTripId(), timestamp);
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
            TripEntity active = tripRepository
                    .findFirstByBusIdAndStatusOrderByStartedAtDesc(bus.getId(), TripStatus.ACTIVE)
                    .orElse(null);
            if (active != null) {
                state = state.withTrip(active.getId(), active.getRoute().getId(),
                        active.getSnapshotIn(), active.getSnapshotOut());
            }
        }
        if (state.lastCompletedRouteId() == null && bus.getLastCompletedRouteId() != null) {
            state = new BusState(state.activeTripId(), state.activeRouteId(),
                    bus.getLastCompletedRouteId(), state.pendingRouteId(), state.inBusParkId(),
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
            UUID                pendingRouteId,       // route assigned while parked at start terminal
            UUID                inBusParkId,
            int                 snapshotIn,
            int                 snapshotOut,
            int                 lastPassedStopSeq,
            Instant             lastSeenAt,
            VehicleLiveSnapshot lastSnapshot
    ) {
        static BusState empty() {
            return new BusState(null, null, null, null, null, 0, 0, 0, null, null);
        }

        BusState withGeofence(UUID parkId) {
            return new BusState(activeTripId, activeRouteId, lastCompletedRouteId,
                    pendingRouteId, parkId, snapshotIn, snapshotOut, lastPassedStopSeq, lastSeenAt, lastSnapshot);
        }

        BusState withPendingRoute(UUID routeId) {
            return new BusState(activeTripId, activeRouteId, lastCompletedRouteId,
                    routeId, inBusParkId, snapshotIn, snapshotOut, lastPassedStopSeq, lastSeenAt, lastSnapshot);
        }

        BusState withTrip(UUID tripId, UUID routeId, int snapIn, int snapOut) {
            return new BusState(tripId, routeId, lastCompletedRouteId,
                    null, inBusParkId, snapIn, snapOut, lastPassedStopSeq, lastSeenAt, lastSnapshot);
        }

        BusState withTripCleared(UUID completedRouteId) {
            return new BusState(null, null, completedRouteId,
                    null, inBusParkId, 0, 0, 0, lastSeenAt, lastSnapshot);
        }

        BusState withTripClearedAndPending(UUID completedRouteId, UUID newPendingRouteId) {
            return new BusState(null, null, completedRouteId,
                    newPendingRouteId, inBusParkId, 0, 0, 0, lastSeenAt, lastSnapshot);
        }

        BusState withStop(int passedSeq) {
            return new BusState(activeTripId, activeRouteId, lastCompletedRouteId,
                    pendingRouteId, inBusParkId, snapshotIn, snapshotOut, passedSeq, lastSeenAt, lastSnapshot);
        }

        BusState withSeen(Instant t, VehicleLiveSnapshot snap) {
            return new BusState(activeTripId, activeRouteId, lastCompletedRouteId,
                    pendingRouteId, inBusParkId, snapshotIn, snapshotOut, lastPassedStopSeq, t, snap);
        }
    }
}
