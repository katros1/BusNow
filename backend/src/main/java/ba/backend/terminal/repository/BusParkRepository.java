package ba.backend.terminal.repository;

import ba.backend.terminal.entity.BusParkEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusParkRepository extends JpaRepository<BusParkEntity, UUID> {
}
