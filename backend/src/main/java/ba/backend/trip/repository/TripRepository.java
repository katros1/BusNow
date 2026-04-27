package ba.backend.trip.repository;

import ba.backend.trip.entity.TripEntity;
import ba.backend.trip.entity.TripStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TripRepository extends JpaRepository<TripEntity, UUID>, JpaSpecificationExecutor<TripEntity> {

    @EntityGraph(attributePaths = {"bus", "route", "route.startBusPark", "route.endBusPark", "route.routeCode"})
    Optional<TripEntity> findByBusIdAndStatus(UUID busId, TripStatus status);

    @EntityGraph(attributePaths = {"bus", "route", "route.routeCode"})
    List<TripEntity> findByStatus(TripStatus status);

    @EntityGraph(attributePaths = {"bus", "route", "route.routeCode"})
    Page<TripEntity> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"bus", "route", "route.routeCode"})
    Page<TripEntity> findByBusId(UUID busId, Pageable pageable);

    @EntityGraph(attributePaths = {"bus", "route", "route.routeCode"})
    Page<TripEntity> findByStatus(TripStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"bus", "route", "route.routeCode"})
    Page<TripEntity> findByBusIdAndStatus(UUID busId, TripStatus status, Pageable pageable);
}
