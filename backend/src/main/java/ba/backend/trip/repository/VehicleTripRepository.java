package ba.backend.trip.repository;

import ba.backend.trip.entity.TripStatus;
import ba.backend.trip.entity.VehicleTripEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleTripRepository extends JpaRepository<VehicleTripEntity, UUID> {
    Optional<VehicleTripEntity> findByBusIdAndStatus(UUID busId, TripStatus status);
}
