package ba.backend.tracking.repository;

import ba.backend.tracking.entity.VehicleLocationEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleLocationRepository extends JpaRepository<VehicleLocationEntity, UUID> {

    List<VehicleLocationEntity> findByTripIdOrderByRecordedAtAsc(UUID tripId);

    Page<VehicleLocationEntity> findByBusIdOrderByRecordedAtDesc(UUID busId, Pageable pageable);
}
