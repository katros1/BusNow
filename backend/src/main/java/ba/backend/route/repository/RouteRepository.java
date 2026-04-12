package ba.backend.route.repository;

import ba.backend.route.entity.RouteEntity;
import ba.backend.route.entity.RouteDirection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteRepository extends JpaRepository<RouteEntity, UUID> {

    @Override
    @EntityGraph(attributePaths = {"startBusPark", "endBusPark", "routeCode", "routeStops", "routeStops.stop"})
    List<RouteEntity> findAll();

    @EntityGraph(attributePaths = {"startBusPark", "endBusPark", "routeCode", "routeStops", "routeStops.stop"})
    Optional<RouteEntity> findById(UUID id);

    @EntityGraph(attributePaths = {"startBusPark", "endBusPark", "routeCode", "routeStops", "routeStops.stop"})
    List<RouteEntity> findByRouteCodeId(UUID routeCodeId);

    @EntityGraph(attributePaths = {"startBusPark", "endBusPark", "routeCode", "routeStops", "routeStops.stop"})
    Optional<RouteEntity> findByRouteCodeIdAndDirection(UUID routeCodeId, RouteDirection direction);
}
