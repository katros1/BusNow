package ba.backend.bus.repository;

import ba.backend.bus.entity.BusEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface BusRepository extends JpaRepository<BusEntity, UUID>, JpaSpecificationExecutor<BusEntity> {

    @Override
    @EntityGraph(attributePaths = {"currentDriver", "routeCode"})
    List<BusEntity> findAll();

    @EntityGraph(attributePaths = {"currentDriver", "routeCode"})
    Optional<BusEntity> findById(UUID id);

    @EntityGraph(attributePaths = {"currentDriver", "routeCode"})
    Optional<BusEntity> findByGpsImei(String gpsImei);

    @Modifying
    @Transactional
    @Query("UPDATE BusEntity b SET b.currentLatitude = :lat, b.currentLongitude = :lon WHERE b.id = :id")
    void updatePosition(@Param("id") UUID id, @Param("lat") Double lat, @Param("lon") Double lon);
}
