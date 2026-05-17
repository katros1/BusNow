package ba.backend.trip.repository;

import ba.backend.trip.entity.TripStatus;
import ba.backend.trip.entity.VehicleTripEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleTripRepository extends JpaRepository<VehicleTripEntity, UUID> {
    Optional<VehicleTripEntity> findFirstByBusIdAndStatusOrderByStartedAtDesc(UUID busId, TripStatus status);

    List<VehicleTripEntity> findAllByBusIdAndStatus(UUID busId, TripStatus status);
}
