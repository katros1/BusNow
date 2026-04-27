package ba.backend.trip.repository;

import ba.backend.trip.entity.TripEntity;
import ba.backend.trip.entity.TripStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripRepository extends JpaRepository<TripEntity, UUID> {

    @EntityGraph(attributePaths = {"bus", "route", "route.startBusPark", "route.endBusPark", "route.routeCode"})
    Optional<TripEntity> findByBusIdAndStatus(UUID busId, TripStatus status);

    @EntityGraph(attributePaths = {"bus", "route", "route.startBusPark", "route.endBusPark", "route.routeCode"})
    List<TripEntity> findByStatus(TripStatus status);
}
