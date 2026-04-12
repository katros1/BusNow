package ba.backend.route.repository;

import ba.backend.route.entity.RouteEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteRepository extends JpaRepository<RouteEntity, UUID> {

    @Override
    @EntityGraph(attributePaths = {"startBusPark", "endBusPark", "routeStops", "routeStops.stop"})
    List<RouteEntity> findAll();

    @EntityGraph(attributePaths = {"startBusPark", "endBusPark", "routeStops", "routeStops.stop"})
    Optional<RouteEntity> findById(UUID id);
}
