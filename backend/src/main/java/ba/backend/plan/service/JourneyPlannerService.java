package ba.backend.plan.service;

import ba.backend.fare.service.FareCalculatorService;
import ba.backend.plan.dto.JourneyNearestStopRequestDto;
import ba.backend.plan.dto.JourneyNearestStopResponseDto;
import ba.backend.plan.dto.JourneyPlanRequestDto;
import ba.backend.plan.dto.JourneyPlanResponseDto;
import ba.backend.plan.osrm.OsrmClient;
import ba.backend.plan.osrm.OsrmWalkResult;
import ba.backend.plan.repository.PlanNearestStopProjection;
import ba.backend.plan.repository.PlanRoutePointProjection;
import ba.backend.route.entity.RouteEntity;
import ba.backend.route.repository.RouteRepository;
import ba.backend.shared.exception.ResourceNotFoundException;
import ba.backend.shared.geo.LineStringGeometryMapper;
import ba.backend.stops.repository.StopRepository;
import ba.backend.tracking.service.GeoUtils;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.locationtech.jts.geom.Coordinate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JourneyPlannerService {

    private static final int DEFAULT_MAX_SUGGESTIONS = 5;
    private static final int ABSOLUTE_MAX_SUGGESTIONS = 5;

    /**
     * Walking quality tiers — used to label the suggestion card in the client.
     *
     *  TIER_1 – excellent: both legs short, barely a walk
     *  TIER_2 – good:      comfortable walk on at least one leg
     *  TIER_3 – fair:      longer walk; still usable, shown last
     */
    private static final double TIER_1_MAX_BOARDING_KM    = 0.40;
    private static final double TIER_1_MAX_ALIGHTING_KM   = 0.40;
    private static final double TIER_2_MAX_TOTAL_KM       = 1.50;

    /**
     * Maximum distance from alighting stop to destination we consider "serving" the destination.
     * Routes beyond this threshold are excluded UNLESS they are the only option.
     */
    private static final double MAX_DESTINATION_WALK_KM = 2.0;

    /**
     * How many candidates are sent to OSRM for real road-distance enrichment.
     */
    private static final int OSRM_ENRICH_LIMIT = 10;

    private final RouteRepository routeRepository;
    private final StopRepository stopRepository;
    private final LineStringGeometryMapper lineStringGeometryMapper;
    private final OsrmClient osrmClient;
    private final FareCalculatorService fareCalculatorService;

    public JourneyPlannerService(
            RouteRepository routeRepository,
            StopRepository stopRepository,
            LineStringGeometryMapper lineStringGeometryMapper,
            OsrmClient osrmClient,
            FareCalculatorService fareCalculatorService
    ) {
        this.routeRepository = routeRepository;
        this.stopRepository = stopRepository;
        this.lineStringGeometryMapper = lineStringGeometryMapper;
        this.osrmClient = osrmClient;
        this.fareCalculatorService = fareCalculatorService;
    }

    /**
     * Plans a journey from {@code currentLocation} to {@code destinationLocation}.
     *
     * <h3>Strategy</h3>
     * <ol>
     *   <li>PostGIS provides straight-line distances from every route point to the user's
     *       origin and destination.</li>
     *   <li>Per route: pick the <em>nearest boarding stop to the user</em> (pure proximity,
     *       no backtrack penalty — user asked for the physically closest stop), then pick the
     *       <em>nearest alighting stop</em> to the destination after boarding.</li>
     *   <li>Routes where the alighting stop is more than {@value MAX_DESTINATION_WALK_KM} km
     *       from the destination are excluded (unless they are the only option).</li>
     *   <li>The top {@value OSRM_ENRICH_LIMIT} candidates are enriched in parallel with real
     *       road-network walking distances and durations from OSRM.</li>
     *   <li>Results are sorted by boarding walk first, then destination distance.</li>
     *   <li>When {@code currentLocation} is null (GPS unavailable), boarding stop is the first
     *       stop of the route and {@code walkToBoardingKm} is returned as null.</li>
     * </ol>
     */
    @Transactional(readOnly = true)
    public JourneyPlanResponseDto plan(JourneyPlanRequestDto request) {
        GeoPoint dest   = parseAndValidatePoint(request.destinationLocation(), "destinationLocation");
        GeoPoint origin = parseOptionalPoint(request.currentLocation());
        boolean hasGps  = (origin != null);
        int limit = clampLimit(request.maxSuggestions());

        // Use destination as dummy origin when GPS unavailable — SQL still computes all distances,
        // but walkToBoardingKm will be overridden to null in the response.
        GeoPoint sqlOrigin = Objects.requireNonNullElse(origin, dest);

        // ── Phase 1: DB — all route points with straight-line distances ───────────
        List<PlanRoutePointProjection> routePoints = routeRepository.findRoutePointsForPlanning(
                sqlOrigin.longitude(), sqlOrigin.latitude(),
                dest.longitude(),   dest.latitude()
        );

        Map<UUID, List<PlanRoutePointProjection>> byRoute = routePoints.stream()
                .collect(Collectors.groupingBy(PlanRoutePointProjection::getRouteId));

        Map<UUID, RouteEntity> routesById = routeRepository.findAll().stream()
                .collect(Collectors.toMap(RouteEntity::getId, Function.identity()));

        // ── Phase 2: one candidate per route ─────────────────────────────────────
        List<RouteCandidate> allCandidates = byRoute.entrySet().stream()
                .map(e -> findBestCandidate(routesById.get(e.getKey()), e.getValue(), hasGps))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingDouble(RouteCandidate::walkToBoardingKmOrZero))
                .collect(Collectors.toList());

        // Filter: only keep routes that actually serve the destination area.
        // Fall back to all candidates if filtering would leave nothing.
        List<RouteCandidate> nearDest = allCandidates.stream()
                .filter(c -> c.distanceToDestinationKm() <= MAX_DESTINATION_WALK_KM)
                .collect(Collectors.toList());
        List<RouteCandidate> candidates = nearDest.isEmpty() ? allCandidates : nearDest;

        // Limit to OSRM enrichment cap
        candidates = candidates.stream().limit(OSRM_ENRICH_LIMIT).collect(Collectors.toList());

        // ── Phase 3: OSRM — real road-network distances (parallel) ───────────────
        GeoPoint finalOrigin = origin;
        List<RouteCandidate> enriched = enrichWithOsrm(candidates, finalOrigin, dest, hasGps);

        enriched.sort(Comparator
                .comparingDouble(RouteCandidate::walkToBoardingKmOrZero)
                .thenComparingDouble(RouteCandidate::distanceToDestinationKm));

        double basePriceFrw = fareCalculatorService.getCurrentBasePriceFrw();

        return new JourneyPlanResponseDto(enriched.stream()
                .limit(limit)
                .map(c -> toSuggestion(c, basePriceFrw, hasGps))
                .toList());
    }

    @Transactional(readOnly = true)
    public JourneyNearestStopResponseDto findNearestStop(JourneyNearestStopRequestDto request) {
        GeoPoint loc = parseAndValidatePoint(request.currentLocation(), "currentLocation");
        PlanNearestStopProjection nearest = stopRepository.findNearestStop(loc.longitude(), loc.latitude());
        if (nearest == null) {
            throw new ResourceNotFoundException("No stops are available.");
        }
        return new JourneyNearestStopResponseDto(
                nearest.getStopId(),
                nearest.getStopName(),
                List.of(nearest.getLongitude(), nearest.getLatitude()),
                round(nearest.getDistanceKm())
        );
    }

    // ── OSRM enrichment ───────────────────────────────────────────────────────

    private List<RouteCandidate> enrichWithOsrm(
            List<RouteCandidate> candidates,
            GeoPoint origin,
            GeoPoint dest,
            boolean hasGps
    ) {
        List<CompletableFuture<RouteCandidate>> futures = candidates.stream()
                .map(c -> CompletableFuture.supplyAsync(() -> enrichCandidate(c, origin, dest, hasGps)))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        return futures.stream()
                .map(f -> {
                    try { return f.get(); }
                    catch (Exception e) { throw new RuntimeException("OSRM enrichment interrupted", e); }
                })
                .collect(Collectors.toList());
    }

    private RouteCandidate enrichCandidate(RouteCandidate c, GeoPoint origin, GeoPoint dest, boolean hasGps) {
        Double boardingKm = null;
        int boardingMin = 0;

        if (hasGps && origin != null) {
            OsrmWalkResult toBoarding = osrmClient.walkingRoute(
                    origin.longitude(), origin.latitude(),
                    c.boarding().getLongitude(), c.boarding().getLatitude()
            ).orElse(null);
            if (toBoarding != null) {
                boardingKm = toBoarding.distanceKm();
                boardingMin = toBoarding.durationMinutes();
            } else {
                boardingKm = c.walkToBoardingKm();
                boardingMin = estimateWalkMinutes(boardingKm != null ? boardingKm : 0);
            }
        }

        OsrmWalkResult toDestination = osrmClient.walkingRoute(
                c.destination().getLongitude(), c.destination().getLatitude(),
                dest.longitude(), dest.latitude()
        ).orElse(null);

        double destinationKm = toDestination != null ? toDestination.distanceKm() : c.distanceToDestinationKm();
        int destinationMin   = toDestination != null ? toDestination.durationMinutes() : estimateWalkMinutes(destinationKm);

        double totalKm  = (boardingKm != null ? boardingKm : 0) + destinationKm;
        int    totalMin = boardingMin + destinationMin;

        return new RouteCandidate(
                c.route(), c.boarding(), c.destination(),
                boardingKm, destinationKm, totalKm,
                boardingMin, destinationMin, totalMin
        );
    }

    private int estimateWalkMinutes(double km) {
        return (int) Math.ceil(km / 5.0 * 60.0);
    }

    // ── Candidate selection ───────────────────────────────────────────────────

    /**
     * Selects the best (boarding, alighting) pair for one route.
     *
     * Boarding: the stop nearest to the user (pure proximity — no direction penalty).
     * Alighting: the stop nearest to the destination that comes after boarding in sequence.
     *
     * When GPS is unavailable ({@code hasGps=false}), the first point on the route
     * (sequence 0, usually the start bus park) is used as boarding.
     */
    private RouteCandidate findBestCandidate(
            RouteEntity route,
            List<PlanRoutePointProjection> points,
            boolean hasGps
    ) {
        if (route == null || points.isEmpty()) return null;

        // ── Boarding: nearest stop to user (no backtrack penalty) ─────────────
        PlanRoutePointProjection boarding;
        if (hasGps) {
            boarding = points.stream()
                    .min(Comparator.comparingDouble(PlanRoutePointProjection::getWalkToBoardingKm))
                    .orElse(null);
        } else {
            // No GPS — pick the route's first point (bus park / first stop)
            boarding = points.stream()
                    .min(Comparator.comparingInt(PlanRoutePointProjection::getPointSequence))
                    .orElse(null);
        }
        if (boarding == null) return null;

        // ── Alighting: nearest to destination, after boarding ─────────────────
        final int boardingSeq = boarding.getPointSequence();
        PlanRoutePointProjection alighting = points.stream()
                .filter(p -> p.getPointSequence() > boardingSeq)
                .min(Comparator.comparingDouble(PlanRoutePointProjection::getWalkToDestinationKm))
                .orElse(null);
        if (alighting == null) return null;

        Double boardingKm    = hasGps ? boarding.getWalkToBoardingKm() : null;
        double destinationKm = alighting.getWalkToDestinationKm();
        double totalKm       = (boardingKm != null ? boardingKm : 0) + destinationKm;

        return new RouteCandidate(
                route, boarding, alighting,
                boardingKm, destinationKm, totalKm,
                0, 0, 0
        );
    }

    // ── DTO mapping ───────────────────────────────────────────────────────────

    private JourneyPlanResponseDto.JourneyRouteSuggestionDto toSuggestion(RouteCandidate c, double basePriceFrw, boolean hasGps) {
        double rideDistanceKm = rideDistanceKm(c);
        double toEndKm        = boardingToEndKm(c);

        Double displayBoardingKm = hasGps ? (c.walkToBoardingKm() != null ? round(c.walkToBoardingKm()) : null) : null;

        return new JourneyPlanResponseDto.JourneyRouteSuggestionDto(
                c.route().getId(),
                c.route().getName(),
                lineStringGeometryMapper.toCoordinates(c.route().getGeo()),
                new JourneyPlanResponseDto.JourneyRoutePointDto(
                        c.boarding().getPointId(),
                        c.boarding().getPointName(),
                        c.boarding().getPointType(),
                        c.boarding().getPointSequence(),
                        List.of(c.boarding().getLongitude(), c.boarding().getLatitude())
                ),
                new JourneyPlanResponseDto.JourneyRoutePointDto(
                        c.destination().getPointId(),
                        c.destination().getPointName(),
                        c.destination().getPointType(),
                        c.destination().getPointSequence(),
                        List.of(c.destination().getLongitude(), c.destination().getLatitude())
                ),
                displayBoardingKm,
                round(c.distanceToDestinationKm()),
                round(c.totalWalkingKm()),
                c.walkToBoardingMinutes(),
                c.distanceToDestinationMinutes(),
                c.totalWalkingMinutes(),
                toTier(c.walkToBoardingKm(), c.distanceToDestinationKm(), c.totalWalkingKm()),
                round(rideDistanceKm),
                fareCalculatorService.calculateFare(basePriceFrw, rideDistanceKm),
                fareCalculatorService.calculateFare(basePriceFrw, toEndKm)
        );
    }

    private double rideDistanceKm(RouteCandidate c) {
        if (c.route().getGeo() == null) {
            return GeoUtils.haversineM(
                    c.boarding().getLatitude(),    c.boarding().getLongitude(),
                    c.destination().getLatitude(), c.destination().getLongitude()
            ) / 1000.0;
        }
        double metres = GeoUtils.distanceAlongLineM(
                c.route().getGeo(),
                c.boarding().getLatitude(),    c.boarding().getLongitude(),
                c.destination().getLatitude(), c.destination().getLongitude()
        );
        return metres / 1000.0;
    }

    private double boardingToEndKm(RouteCandidate c) {
        if (c.route().getGeo() == null) return rideDistanceKm(c);
        Coordinate[] coords = c.route().getGeo().getCoordinates();
        Coordinate last = coords[coords.length - 1];
        double metres = GeoUtils.distanceAlongLineM(
                c.route().getGeo(),
                c.boarding().getLatitude(), c.boarding().getLongitude(),
                last.y, last.x
        );
        return metres / 1000.0;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String toTier(Double boardingKm, double alightingKm, double totalKm) {
        double b = boardingKm != null ? boardingKm : 0;
        if (b <= TIER_1_MAX_BOARDING_KM && alightingKm <= TIER_1_MAX_ALIGHTING_KM) return "TIER_1";
        if (totalKm <= TIER_2_MAX_TOTAL_KM) return "TIER_2";
        return "TIER_3";
    }

    private int clampLimit(Integer requested) {
        if (requested == null) return DEFAULT_MAX_SUGGESTIONS;
        return Math.min(requested, ABSOLUTE_MAX_SUGGESTIONS);
    }

    private GeoPoint parseAndValidatePoint(List<Double> coords, String field) {
        if (coords == null || coords.size() != 2 || coords.get(0) == null || coords.get(1) == null) {
            throw new IllegalArgumentException(field + " must contain exactly [longitude, latitude].");
        }
        double lng = coords.get(0);
        double lat = coords.get(1);
        if (lng < -180 || lng > 180) throw new IllegalArgumentException(field + " longitude out of range.");
        if (lat < -90  || lat > 90)  throw new IllegalArgumentException(field + " latitude out of range.");
        return new GeoPoint(lng, lat);
    }

    private GeoPoint parseOptionalPoint(List<Double> coords) {
        if (coords == null || coords.size() != 2) return null;
        Double lng = coords.get(0);
        Double lat = coords.get(1);
        if (lng == null || lat == null) return null;
        return new GeoPoint(lng, lat);
    }

    private double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    // ── Internal models ───────────────────────────────────────────────────────

    private record GeoPoint(double longitude, double latitude) {}

    private record RouteCandidate(
            RouteEntity route,
            PlanRoutePointProjection boarding,
            PlanRoutePointProjection destination,
            Double walkToBoardingKm,           // null when GPS unavailable
            double distanceToDestinationKm,
            double totalWalkingKm,
            int walkToBoardingMinutes,
            int distanceToDestinationMinutes,
            int totalWalkingMinutes
    ) {
        double walkToBoardingKmOrZero() {
            return walkToBoardingKm != null ? walkToBoardingKm : 0;
        }
    }
}
