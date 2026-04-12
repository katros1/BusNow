package ba.backend.routecode.service;

import ba.backend.route.entity.RouteDirection;
import ba.backend.route.entity.RouteEntity;
import ba.backend.route.repository.RouteRepository;
import ba.backend.routecode.dto.RouteCodeCreateDto;
import ba.backend.routecode.dto.RouteCodeResponseDto;
import ba.backend.routecode.dto.RouteCodeUpdateDto;
import ba.backend.routecode.dto.RouteSummaryDto;
import ba.backend.routecode.entity.RouteCodeEntity;
import ba.backend.routecode.repository.RouteCodeRepository;
import ba.backend.shared.exception.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RouteCodeService {

    private final RouteCodeRepository routeCodeRepository;
    private final RouteRepository routeRepository;

    public RouteCodeService(RouteCodeRepository routeCodeRepository, RouteRepository routeRepository) {
        this.routeCodeRepository = routeCodeRepository;
        this.routeRepository = routeRepository;
    }

    @Transactional
    public RouteCodeResponseDto create(RouteCodeCreateDto request) {
        validateRoutePair(request.forwardRouteId(), request.backwardRouteId());
        RouteCodeEntity routeCode = routeCodeRepository.save(new RouteCodeEntity(normalizedRequired(request.code(), "code")));
        assignDirections(routeCode, request.forwardRouteId(), request.backwardRouteId());
        return toDto(routeCode);
    }

    @Transactional(readOnly = true)
    public List<RouteCodeResponseDto> list() {
        return routeCodeRepository.findAll().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public RouteCodeResponseDto get(UUID id) {
        return toDto(findRouteCode(id));
    }

    @Transactional
    public RouteCodeResponseDto update(UUID id, RouteCodeCreateDto request) {
        validateRoutePair(request.forwardRouteId(), request.backwardRouteId());
        RouteCodeEntity routeCode = findRouteCode(id);
        routeCode.setCode(normalizedRequired(request.code(), "code"));
        routeCode = routeCodeRepository.save(routeCode);
        assignDirections(routeCode, request.forwardRouteId(), request.backwardRouteId());
        return toDto(routeCode);
    }

    @Transactional
    public RouteCodeResponseDto patch(UUID id, RouteCodeUpdateDto request) {
        if (request.code() == null && request.forwardRouteId() == null && request.backwardRouteId() == null) {
            throw new IllegalArgumentException("At least one field must be provided for patch.");
        }

        RouteCodeEntity routeCode = findRouteCode(id);
        if (request.code() != null) {
            routeCode.setCode(normalizedRequired(request.code(), "code"));
        }
        routeCode = routeCodeRepository.save(routeCode);

        if (request.forwardRouteId() != null || request.backwardRouteId() != null) {
            UUID currentForwardRouteId = routeRepository.findByRouteCodeIdAndDirection(id, RouteDirection.FORWARD)
                    .map(RouteEntity::getId)
                    .orElseThrow(() -> new IllegalArgumentException("Forward route is not assigned yet."));
            UUID currentBackwardRouteId = routeRepository.findByRouteCodeIdAndDirection(id, RouteDirection.BACKWARD)
                    .map(RouteEntity::getId)
                    .orElseThrow(() -> new IllegalArgumentException("Backward route is not assigned yet."));

            UUID forwardId = request.forwardRouteId() == null ? currentForwardRouteId : request.forwardRouteId();
            UUID backwardId = request.backwardRouteId() == null ? currentBackwardRouteId : request.backwardRouteId();
            validateRoutePair(forwardId, backwardId);
            assignDirections(routeCode, forwardId, backwardId);
        }

        return toDto(routeCode);
    }

    @Transactional
    public void delete(UUID id) {
        RouteCodeEntity routeCode = findRouteCode(id);
        clearCurrentAssignments(routeCode);
        routeCodeRepository.delete(routeCode);
    }

    private RouteCodeEntity findRouteCode(UUID id) {
        return routeCodeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Route code not found for id: " + id));
    }

    private RouteEntity findRoute(UUID id) {
        return routeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Route not found for id: " + id));
    }

    private void validateRoutePair(UUID forwardRouteId, UUID backwardRouteId) {
        if (forwardRouteId.equals(backwardRouteId)) {
            throw new IllegalArgumentException("Forward and backward route must be different.");
        }
    }

    private void clearCurrentAssignments(RouteCodeEntity routeCode) {
        List<RouteEntity> currentlyAssigned = routeRepository.findByRouteCodeId(routeCode.getId());
        for (RouteEntity route : currentlyAssigned) {
            route.setRouteCode(null);
            route.setDirection(null);
        }
        routeRepository.saveAll(currentlyAssigned);
    }

    private void assignDirections(RouteCodeEntity routeCode, UUID forwardRouteId, UUID backwardRouteId) {
        RouteEntity forwardRoute = findRoute(forwardRouteId);
        RouteEntity backwardRoute = findRoute(backwardRouteId);

        assertAssignableToCode(forwardRoute, routeCode);
        assertAssignableToCode(backwardRoute, routeCode);

        clearCurrentAssignments(routeCode);

        forwardRoute.setRouteCode(routeCode);
        forwardRoute.setDirection(RouteDirection.FORWARD);
        backwardRoute.setRouteCode(routeCode);
        backwardRoute.setDirection(RouteDirection.BACKWARD);
        routeRepository.saveAll(List.of(forwardRoute, backwardRoute));
    }

    private void assertAssignableToCode(RouteEntity route, RouteCodeEntity routeCode) {
        if (route.getRouteCode() != null && !route.getRouteCode().getId().equals(routeCode.getId())) {
            throw new IllegalArgumentException("Route " + route.getId() + " is already assigned to another route code.");
        }
    }

    private String normalizedRequired(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank.");
        }
        return value.trim();
    }

    private RouteCodeResponseDto toDto(RouteCodeEntity entity) {
        RouteSummaryDto forwardRoute = routeRepository.findByRouteCodeIdAndDirection(entity.getId(), RouteDirection.FORWARD)
                .map(route -> new RouteSummaryDto(route.getId(), route.getName()))
                .orElse(null);
        RouteSummaryDto backwardRoute = routeRepository.findByRouteCodeIdAndDirection(entity.getId(), RouteDirection.BACKWARD)
                .map(route -> new RouteSummaryDto(route.getId(), route.getName()))
                .orElse(null);

        return new RouteCodeResponseDto(
                entity.getId(),
                entity.getCode(),
                forwardRoute,
                backwardRoute,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
