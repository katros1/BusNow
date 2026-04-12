package ba.backend.driver.repository;

import ba.backend.driver.entity.DriverEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriverRepository extends JpaRepository<DriverEntity, UUID> {
}
