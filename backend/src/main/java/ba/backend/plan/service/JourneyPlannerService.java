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
    private static final double MAX_RECOMMENDED_WALKING_KM = 3.0;
    private static final double TIER_1_MAX_WALKING_KM = 1.0;
    private static final double TIER_2_MAX_WALKING_KM = 2.0;

    /**
     * How many straight-line candidates are sent to OSRM for real-distance enrichment.
     * Straight-line pre-filtering keeps the number of OSRM calls bounded.
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
     * <p>Strategy:
     * <ol>
     *   <li>PostGIS {@code ST_DistanceSphere} is used to annotate every route point
     *       (start bus park → stops → end bus park) in the DB with its straight-line
     *       distance to the origin and destination.  This is fast and lets us filter
     *       down to the best candidates before hitting OSRM.</li>
     *   <li>The best (boarding, alighting) pair is chosen per route — the pair that
     *       minimises total straight-line walking distance, with alighting always
     *       after boarding in route sequence.</li>
     *   <li>The top {@value OSRM_ENRICH_LIMIT} candidates are enriched in parallel
     *       with <em>real road-network distances and walking durations</em> from OSRM.
     *       This is essential for Rwanda: the country's hilly terrain means Haversine
     *       distances can be 30-50 % shorter than the actual walking path.</li>
     *   <li>Candidates are re-ranked by OSRM distance and filtered by the recommended
     *       walking budget.</li>
     * </ol>
     */
    @Transactional(readOnly = true)
    public JourneyPlanResponseDto plan(JourneyPlanRequestDto request) {
        GeoPoint origin = parseAndValidatePoint(request.currentLocation(), "currentLocation");
        GeoPoint dest   = parseAndValidatePoint(request.destinationLocation(), "destinationLocation");
        int limit = clampLimit(request.maxSuggestions());

        // ── Phase 1: DB — collect route points with straight-line distances ──────
        List<PlanRoutePointProjection> routePoints = routeRepository.findRoutePointsForPlanning(
                origin.longitude(), origin.latitude(),
                dest.longitude(),   dest.latitude()
        );

        Map<UUID, List<PlanRoutePointProjection>> byRoute = routePoints.stream()
                .collect(Collectors.groupingBy(PlanRoutePointProjection::getRouteId));

        Map<UUID, RouteEntity> routesById = routeRepository.findAll().stream()
                .collect(Collectors.toMap(RouteEntity::getId, Function.identity()));

        // ── Phase 2: best (boarding, alighting) per route by straight-line ───────
        List<RouteCandidate> candidates = byRoute.entrySet().stream()
                .map(e -> findBestCandidate(routesById.get(e.getKey()), e.getValue()))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingDouble(RouteCandidate::totalWalkingKm))
                .limit(OSRM_ENRICH_LIMIT)
                .collect(Collectors.toList());

        // ── Phase 3: OSRM — real road-network distances (parallel) ───────────────
        List<RouteCandidate> enriched = enrichWithOsrm(candidates, origin, dest);
        enriched.sort(Comparator.comparingDouble(RouteCandidate::totalWalkingKm));

        // ── Phase 4: apply walking budget; fall back to all enriched if none fit ─
        List<RouteCandidate> eligible = enriched.stream()
                .filter(c -> c.totalWalkingKm() <= MAX_RECOMMENDED_WALKING_KM)
                .toList();
        List<RouteCandidate> selected = eligible.isEmpty() ? enriched : eligible;

        double basePriceFrw = fareCalculatorService.getCurrentBasePriceFrw();

        return new JourneyPlanResponseDto(selected.stream()
                .limit(limit)
                .map(c -> toSuggestion(c, basePriceFrw))
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

    /**
     * For each candidate, fires two parallel OSRM walking requests:
     *   • origin → boarding point
     *   • alighting point → destination
     *
     * If OSRM is unreachable for a leg, the straight-line distance is kept as fallback
     * so the response is always returned even when OSRM is temporarily down.
     */
    private List<RouteCandidate> enrichWithOsrm(
            List<RouteCandidate> candidates,
            GeoPoint origin,
            GeoPoint dest
    ) {
        List<CompletableFuture<RouteCandidate>> futures = candidates.stream()
                .map(c -> CompletableFuture.supplyAsync(() -> enrichCandidate(c, origin, dest)))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        return futures.stream()
                .map(f -> {
                    try {
                        return f.get();
                    } catch (Exception e) {
                        throw new RuntimeException("OSRM enrichment interrupted", e);
                    }
                })
                .collect(Collectors.toList());
    }

    private RouteCandidate enrichCandidate(RouteCandidate c, GeoPoint origin, GeoPoint dest) {
        OsrmWalkResult toBoarding = osrmClient.walkingRoute(
                origin.longitude(), origin.latitude(),
                c.boarding().getLongitude(), c.boarding().getLatitude()
        ).orElse(null);

        OsrmWalkResult toDestination = osrmClient.walkingRoute(
                c.destination().getLongitude(), c.destination().getLatitude(),
                dest.longitude(), dest.latitude()
        ).orElse(null);

        double boardingKm    = toBoarding    != null ? toBoarding.distanceKm()    : c.walkToBoardingKm();
        double destinationKm = toDestination != null ? toDestination.distanceKm() : c.walkToDestinationKm();

        // When OSRM is unavailable for a leg, estimate minutes from the distance we have.
        // OSRM's duration is preferred because it accounts for road network and hills.
        int boardingMin    = toBoarding    != null ? toBoarding.durationMinutes()    : estimateWalkMinutes(boardingKm);
        int destinationMin = toDestination != null ? toDestination.durationMinutes() : estimateWalkMinutes(destinationKm);

        return new RouteCandidate(
                c.route(), c.boarding(), c.destination(),
                boardingKm, destinationKm, boardingKm + destinationKm,
                boardingMin, destinationMin
        );
    }

    /** Estimates walking time at 5 km/h, rounded up to the nearest minute. */
    private int estimateWalkMinutes(double km) {
        return (int) Math.ceil(km / 5.0 * 60.0);
    }

    // ── Candidate selection ───────────────────────────────────────────────────

    private RouteCandidate findBestCandidate(
            RouteEntity route,
            List<PlanRoutePointProjection> points
    ) {
        if (route == null || points.size() < 2) return null;

        RouteCandidate best = null;
        for (int i = 0; i < points.size() - 1; i++) {
            PlanRoutePointProjection boarding  = points.get(i);
            double toBoarding = boarding.getWalkToBoardingKm();
            for (int j = i + 1; j < points.size(); j++) {
                PlanRoutePointProjection alighting = points.get(j);
                double total = toBoarding + alighting.getWalkToDestinationKm();
                if (best == null || total < best.totalWalkingKm()) {
                    // Walking minutes are 0 here; OSRM enrichment fills them in.
                    best = new RouteCandidate(
                            route, boarding, alighting,
                            toBoarding, alighting.getWalkToDestinationKm(), total,
                            0, 0
                    );
                }
            }
        }
        return best;
    }

    // ── DTO mapping ───────────────────────────────────────────────────────────

    private JourneyPlanResponseDto.JourneyRouteSuggestionDto toSuggestion(RouteCandidate c, double basePriceFrw) {
        double rideDistanceKm = rideDistanceKm(c);
        double toEndKm        = boardingToEndKm(c);

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
                round(c.walkToBoardingKm()),
                round(c.walkToDestinationKm()),
                round(c.totalWalkingKm()),
                c.walkToBoardingMinutes(),
                c.walkToDestinationMinutes(),
                c.walkToBoardingMinutes() + c.walkToDestinationMinutes(),
                toTier(c.totalWalkingKm()),
                round(rideDistanceKm),
                fareCalculatorService.calculateFare(basePriceFrw, rideDistanceKm),
                fareCalculatorService.calculateFare(basePriceFrw, toEndKm)
        );
    }

    /** Distance along the route polyline from the boarding point to the destination point. */
    private double rideDistanceKm(RouteCandidate c) {
        double metres = GeoUtils.distanceAlongLineM(
                c.route().getGeo(),
                c.boarding().getLatitude(),    c.boarding().getLongitude(),
                c.destination().getLatitude(), c.destination().getLongitude()
        );
        return metres / 1000.0;
    }

    /**
     * Distance along the route from the boarding point to the very last coordinate (end bus park).
     * This is the worst-case ride length: used to compute the minimum card balance needed to board.
     */
    private double boardingToEndKm(RouteCandidate c) {
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

    private String toTier(double totalWalkingKm) {
        if (totalWalkingKm <= TIER_1_MAX_WALKING_KM) return "TIER_1";
        if (totalWalkingKm <= TIER_2_MAX_WALKING_KM) return "TIER_2";
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
        if (lng < -180 || lng > 180) throw new IllegalArgumentException(field + " longitude must be between -180 and 180.");
        if (lat < -90  || lat > 90)  throw new IllegalArgumentException(field + " latitude must be between -90 and 90.");
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
            double walkToBoardingKm,
            double walkToDestinationKm,
            double totalWalkingKm,
            int walkToBoardingMinutes,
            int walkToDestinationMinutes
    ) {}
}
