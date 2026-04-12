package ba.backend.bus.repository;

import ba.backend.bus.entity.BusEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusRepository extends JpaRepository<BusEntity, UUID> {

    @Override
    @EntityGraph(attributePaths = {"currentDriver", "routeCode"})
    List<BusEntity> findAll();

    @EntityGraph(attributePaths = {"currentDriver", "routeCode"})
    Optional<BusEntity> findById(UUID id);
}
