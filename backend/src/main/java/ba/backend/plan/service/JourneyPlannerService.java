package ba.backend.plan.service;

import ba.backend.plan.dto.JourneyNearestStopRequestDto;
import ba.backend.plan.dto.JourneyNearestStopResponseDto;
import ba.backend.plan.dto.JourneyPlanRequestDto;
import ba.backend.plan.dto.JourneyPlanResponseDto;
import ba.backend.plan.repository.PlanNearestStopProjection;
import ba.backend.plan.repository.PlanRoutePointProjection;
import ba.backend.route.entity.RouteEntity;
import ba.backend.route.repository.RouteRepository;
import ba.backend.shared.geo.LineStringGeometryMapper;
import ba.backend.shared.exception.ResourceNotFoundException;
import ba.backend.stops.repository.StopRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JourneyPlannerService {

    private static final int DEFAULT_MAX_SUGGESTIONS = 5;
    private static final int ABSOLUTE_MAX_SUGGESTIONS = 5;
    private static final double MAX_RECOMMENDED_WALKING_KM = 3.0;
    private static final double TIER_1_MAX_WALKING_KM = 1.0;
    private static final double TIER_2_MAX_WALKING_KM = 2.0;
    private final RouteRepository routeRepository;
    private final StopRepository stopRepository;
    private final LineStringGeometryMapper lineStringGeometryMapper;

    public JourneyPlannerService(
            RouteRepository routeRepository,
            StopRepository stopRepository,
            LineStringGeometryMapper lineStringGeometryMapper
    ) {
        this.routeRepository = routeRepository;
        this.stopRepository = stopRepository;
        this.lineStringGeometryMapper = lineStringGeometryMapper;
    }

    @Transactional(readOnly = true)
    public JourneyPlanResponseDto plan(JourneyPlanRequestDto request) {
        GeoPoint currentLocation = parseAndValidatePoint(request.currentLocation(), "currentLocation");
        GeoPoint destinationLocation = parseAndValidatePoint(request.destinationLocation(), "destinationLocation");
        int suggestionLimit = request.maxSuggestions() == null ? DEFAULT_MAX_SUGGESTIONS : request.maxSuggestions();
        suggestionLimit = Math.min(suggestionLimit, ABSOLUTE_MAX_SUGGESTIONS);

        List<PlanRoutePointProjection> routePoints = routeRepository.findRoutePointsForPlanning(
                currentLocation.longitude(),
                currentLocation.latitude(),
                destinationLocation.longitude(),
                destinationLocation.latitude()
        );

        Map<UUID, List<PlanRoutePointProjection>> routePointsByRoute = routePoints.stream()
                .collect(Collectors.groupingBy(PlanRoutePointProjection::getRouteId));
        Map<UUID, RouteEntity> routesById = routeRepository.findAll().stream()
                .collect(Collectors.toMap(RouteEntity::getId, Function.identity()));

        List<RouteCandidate> allCandidates = new ArrayList<>();
        for (Map.Entry<UUID, List<PlanRoutePointProjection>> entry : routePointsByRoute.entrySet()) {
            RouteEntity route = routesById.get(entry.getKey());
            if (route == null) {
                continue;
            }
            RouteCandidate bestForRoute = findBestCandidate(route, entry.getValue());
            if (bestForRoute != null) {
                allCandidates.add(bestForRoute);
            }
        }
        allCandidates.sort(Comparator.comparingDouble(RouteCandidate::totalWalkingKm));

        List<RouteCandidate> eligibleCandidates = allCandidates.stream()
                .filter(candidate -> candidate.totalWalkingKm() <= MAX_RECOMMENDED_WALKING_KM)
                .toList();

        List<RouteCandidate> selectedCandidates = eligibleCandidates.isEmpty() ? allCandidates : eligibleCandidates;

        return new JourneyPlanResponseDto(selectedCandidates.stream()
                .limit(suggestionLimit)
                .map(this::toSuggestion)
                .toList());
    }

    @Transactional(readOnly = true)
    public JourneyNearestStopResponseDto findNearestStop(JourneyNearestStopRequestDto request) {
        GeoPoint currentLocation = parseAndValidatePoint(request.currentLocation(), "currentLocation");
        PlanNearestStopProjection nearestStop = stopRepository.findNearestStop(
                currentLocation.longitude(),
                currentLocation.latitude()
        );

        if (nearestStop == null) {
            throw new ResourceNotFoundException("No stops are available.");
        }

        return new JourneyNearestStopResponseDto(
                nearestStop.getStopId(),
                nearestStop.getStopName(),
                List.of(nearestStop.getLongitude(), nearestStop.getLatitude()),
                round(nearestStop.getDistanceKm())
        );
    }

    private RouteCandidate findBestCandidate(RouteEntity route, List<PlanRoutePointProjection> routePoints) {
        if (routePoints.size() < 2) {
            return null;
        }

        RouteCandidate best = null;
        for (int i = 0; i < routePoints.size() - 1; i++) {
            PlanRoutePointProjection boarding = routePoints.get(i);
            double walkToBoardingKm = boarding.getWalkToBoardingKm();

            for (int j = i + 1; j < routePoints.size(); j++) {
                PlanRoutePointProjection destination = routePoints.get(j);
                double walkToDestinationKm = destination.getWalkToDestinationKm();
                double totalWalkingKm = walkToBoardingKm + walkToDestinationKm;

                if (best == null || totalWalkingKm < best.totalWalkingKm()) {
                    best = new RouteCandidate(
                            route,
                            boarding,
                            destination,
                            walkToBoardingKm,
                            walkToDestinationKm,
                            totalWalkingKm
                    );
                }
            }
        }
        return best;
    }

    private JourneyPlanResponseDto.JourneyRouteSuggestionDto toSuggestion(RouteCandidate candidate) {
        return new JourneyPlanResponseDto.JourneyRouteSuggestionDto(
                candidate.route().getId(),
                candidate.route().getName(),
                lineStringGeometryMapper.toCoordinates(candidate.route().getGeo()),
                new JourneyPlanResponseDto.JourneyRoutePointDto(
                        candidate.boarding().getPointId(),
                        candidate.boarding().getPointName(),
                        candidate.boarding().getPointType(),
                        candidate.boarding().getPointSequence(),
                        List.of(candidate.boarding().getLongitude(), candidate.boarding().getLatitude())
                ),
                new JourneyPlanResponseDto.JourneyRoutePointDto(
                        candidate.destination().getPointId(),
                        candidate.destination().getPointName(),
                        candidate.destination().getPointType(),
                        candidate.destination().getPointSequence(),
                        List.of(candidate.destination().getLongitude(), candidate.destination().getLatitude())
                ),
                round(candidate.walkToBoardingKm()),
                round(candidate.walkToDestinationKm()),
                round(candidate.totalWalkingKm()),
                toTier(candidate.totalWalkingKm())
        );
    }

    private String toTier(double totalWalkingKm) {
        if (totalWalkingKm <= TIER_1_MAX_WALKING_KM) {
            return "TIER_1";
        }
        if (totalWalkingKm <= TIER_2_MAX_WALKING_KM) {
            return "TIER_2";
        }
        return "TIER_3";
    }

    private GeoPoint parseAndValidatePoint(List<Double> coordinates, String fieldName) {
        if (coordinates == null || coordinates.size() != 2 || coordinates.get(0) == null || coordinates.get(1) == null) {
            throw new IllegalArgumentException(fieldName + " must contain exactly [longitude, latitude].");
        }
        double longitude = coordinates.get(0);
        double latitude = coordinates.get(1);
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException(fieldName + " longitude must be between -180 and 180.");
        }
        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException(fieldName + " latitude must be between -90 and 90.");
        }
        return new GeoPoint(longitude, latitude);
    }

    private double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private record GeoPoint(double longitude, double latitude) {
    }

    private record RouteCandidate(
            RouteEntity route,
            PlanRoutePointProjection boarding,
            PlanRoutePointProjection destination,
            double walkToBoardingKm,
            double walkToDestinationKm,
            double totalWalkingKm
    ) {
    }
}
