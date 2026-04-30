package ba.backend.tracking.repository;

import ba.backend.tracking.dto.ReplayTripDto;
import ba.backend.tracking.entity.VehicleLocationEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VehicleLocationRepository extends JpaRepository<VehicleLocationEntity, UUID> {

    List<VehicleLocationEntity> findByTripIdOrderByRecordedAtAsc(UUID tripId);

    Page<VehicleLocationEntity> findByBusIdOrderByRecordedAtDesc(UUID busId, Pageable pageable);

    @Query("""
        SELECT vl FROM VehicleLocationEntity vl
        JOIN FETCH vl.bus
        LEFT JOIN FETCH vl.trip t
        LEFT JOIN FETCH t.route r
        LEFT JOIN FETCH r.routeCode
        WHERE t.id = :tripId
        ORDER BY vl.recordedAt ASC
        """)
    List<VehicleLocationEntity> findByTripIdWithDetails(@Param("tripId") UUID tripId);

    @Query("""
        SELECT new ba.backend.tracking.dto.ReplayTripDto(
            t.id, b.id, b.plateNumber, r.name,
            COUNT(vl), MIN(vl.recordedAt), MAX(vl.recordedAt), t.status
        )
        FROM VehicleLocationEntity vl
        JOIN vl.trip t
        JOIN t.bus b
        JOIN t.route r
        GROUP BY t.id, b.id, b.plateNumber, r.name, t.status
        ORDER BY MAX(vl.recordedAt) DESC
        """)
    List<ReplayTripDto> findTripsWithFrameCounts();
}
