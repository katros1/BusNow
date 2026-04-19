package ba.backend.bus.repository;

import ba.backend.bus.entity.BusEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface BusRepository extends JpaRepository<BusEntity, UUID>, JpaSpecificationExecutor<BusEntity> {

    @Override
    @EntityGraph(attributePaths = {"currentDriver", "routeCode"})
    List<BusEntity> findAll();

    @EntityGraph(attributePaths = {"currentDriver", "routeCode"})
    Optional<BusEntity> findById(UUID id);
}
