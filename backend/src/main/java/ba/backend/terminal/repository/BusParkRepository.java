package ba.backend.terminal.repository;

import ba.backend.terminal.entity.BusParkEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface BusParkRepository extends JpaRepository<BusParkEntity, UUID>, JpaSpecificationExecutor<BusParkEntity> {
}
