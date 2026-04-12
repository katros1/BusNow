package ba.backend.route.service;

import ba.backend.route.dto.RouteBusParkRefDto;
import ba.backend.route.dto.RouteBusParkShapeDto;
import ba.backend.route.dto.RouteCreateDto;
import ba.backend.route.dto.RouteDetailResponseDto;
import ba.backend.route.dto.RouteResponseDto;
import ba.backend.route.dto.RouteStopResponseDto;
import ba.backend.route.dto.RouteStopShapeDto;
import ba.backend.route.dto.RouteStopsAssignmentDto;
import ba.backend.route.dto.RouteUpdateDto;
import ba.backend.route.entity.RouteEntity;
import ba.backend.route.entity.RouteStopEntity;
import ba.backend.route.repository.RouteRepository;
import ba.backend.shared.exception.ResourceNotFoundException;
import ba.backend.shared.geo.LineStringGeometryMapper;
import ba.backend.shared.geo.PolygonGeometryMapper;
import ba.backend.stops.entity.StopEntity;
import ba.backend.stops.repository.StopRepository;
import ba.backend.terminal.entity.BusParkEntity;
import ba.backend.terminal.repository.BusParkRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RouteService {

    private final RouteRepository routeRepository;
    private final BusParkRepository busParkRepository;
    private final StopRepository stopRepository;
    private final LineStringGeometryMapper lineStringGeometryMapper;
    private final PolygonGeometryMapper polygonGeometryMapper;

    public RouteService(
            RouteRepository routeRepository,
            BusParkRepository busParkRepository,
            StopRepository stopRepository,
            LineStringGeometryMapper lineStringGeometryMapper,
            PolygonGeometryMapper polygonGeometryMapper
    ) {
        this.routeRepository = routeRepository;
        this.busParkRepository = busParkRepository;
        this.stopRepository = stopRepository;
        this.lineStringGeometryMapper = lineStringGeometryMapper;
        this.polygonGeometryMapper = polygonGeometryMapper;
    }

    @Transactional
    public RouteResponseDto create(RouteCreateDto request) {
        RouteEntity entity = new RouteEntity(
                request.name().trim(),
                lineStringGeometryMapper.toLineString(request.coordinates()),
                findBusPark(request.startBusParkId(), "Start bus park"),
                findBusPark(request.endBusParkId(), "End bus park")
        );
        return toDto(routeRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<RouteResponseDto> list() {
        return routeRepository.findAll().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public RouteDetailResponseDto get(UUID id) {
        return toDetailDto(findRoute(id));
    }

    @Transactional
    public RouteResponseDto update(UUID id, RouteCreateDto request) {
        RouteEntity route = findRoute(id);
        route.setName(request.name().trim());
        route.setGeo(lineStringGeometryMapper.toLineString(request.coordinates()));
        route.setStartBusPark(findBusPark(request.startBusParkId(), "Start bus park"));
        route.setEndBusPark(findBusPark(request.endBusParkId(), "End bus park"));
        return toDto(routeRepository.save(route));
    }

    @Transactional
    public RouteResponseDto patch(UUID id, RouteUpdateDto request) {
        if (request.name() == null
                && request.coordinates() == null
                && request.startBusParkId() == null
                && request.endBusParkId() == null) {
            throw new IllegalArgumentException("At least one field must be provided for patch.");
        }

        RouteEntity route = findRoute(id);

        if (request.name() != null) {
            if (request.name().isBlank()) {
                throw new IllegalArgumentException("Name must not be blank.");
            }
            route.setName(request.name().trim());
        }
        if (request.coordinates() != null) {
            route.setGeo(lineStringGeometryMapper.toLineString(request.coordinates()));
        }
        if (request.startBusParkId() != null) {
            route.setStartBusPark(findBusPark(request.startBusParkId(), "Start bus park"));
        }
        if (request.endBusParkId() != null) {
            route.setEndBusPark(findBusPark(request.endBusParkId(), "End bus park"));
        }

        return toDto(routeRepository.save(route));
    }

    @Transactional
    public void delete(UUID id) {
        if (!routeRepository.existsById(id)) {
            throw new ResourceNotFoundException("Route not found for id: " + id);
        }
        routeRepository.deleteById(id);
    }

    @Transactional
    public RouteResponseDto assignStops(UUID id, RouteStopsAssignmentDto request) {
        RouteEntity route = findRoute(id);
        List<UUID> stopIds = request.stopIds();

        if (stopIds == null) {
            throw new IllegalArgumentException("stopIds must not be null.");
        }

        if (stopIds.isEmpty()) {
            route.getRouteStops().clear();
            return toDto(routeRepository.save(route));
        }

        if (stopIds.stream().distinct().count() != stopIds.size()) {
            throw new IllegalArgumentException("stopIds must not contain duplicates.");
        }

        List<StopEntity> stops = stopRepository.findAllById(stopIds);
        if (stops.size() != stopIds.size()) {
            throw new ResourceNotFoundException("One or more stops were not found.");
        }

        Map<UUID, StopEntity> stopById = new HashMap<>();
        for (StopEntity stop : stops) {
            stopById.put(stop.getId(), stop);
        }

        route.getRouteStops().clear();
        int sequence = 1;
        for (UUID stopId : stopIds) {
            StopEntity stop = stopById.get(stopId);
            if (stop == null) {
                throw new ResourceNotFoundException("Stop not found for id: " + stopId);
            }
            route.getRouteStops().add(new RouteStopEntity(route, stop, sequence++));
        }

        return toDto(routeRepository.save(route));
    }

    private RouteEntity findRoute(UUID id) {
        return routeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Route not found for id: " + id));
    }

    private BusParkEntity findBusPark(UUID id, String label) {
        return busParkRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(label + " not found for id: " + id));
    }

    private RouteResponseDto toDto(RouteEntity route) {
        return new RouteResponseDto(
                route.getId(),
                route.getName(),
                new RouteBusParkRefDto(route.getStartBusPark().getId(), route.getStartBusPark().getName()),
                new RouteBusParkRefDto(route.getEndBusPark().getId(), route.getEndBusPark().getName()),
                route.getRouteStops().stream()
                        .map(routeStop -> new RouteStopResponseDto(routeStop.getStop().getId(), routeStop.getSequence()))
                        .toList(),
                route.getCreatedAt(),
                route.getUpdatedAt()
        );
    }

    private RouteDetailResponseDto toDetailDto(RouteEntity route) {
        return new RouteDetailResponseDto(
                route.getId(),
                route.getName(),
                lineStringGeometryMapper.toCoordinates(route.getGeo()),
                new RouteBusParkShapeDto(
                        route.getStartBusPark().getId(),
                        route.getStartBusPark().getName(),
                        polygonGeometryMapper.toCoordinates(route.getStartBusPark().getPolygon())
                ),
                new RouteBusParkShapeDto(
                        route.getEndBusPark().getId(),
                        route.getEndBusPark().getName(),
                        polygonGeometryMapper.toCoordinates(route.getEndBusPark().getPolygon())
                ),
                route.getRouteStops().stream()
                        .map(routeStop -> new RouteStopShapeDto(
                                routeStop.getStop().getId(),
                                routeStop.getStop().getName(),
                                routeStop.getSequence(),
                                polygonGeometryMapper.toCoordinates(routeStop.getStop().getGeo())
                        ))
                        .toList(),
                route.getCreatedAt(),
                route.getUpdatedAt()
        );
    }
}
