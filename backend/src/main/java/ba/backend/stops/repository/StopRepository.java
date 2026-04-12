package ba.backend.stops.repository;

import ba.backend.stops.entity.StopEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StopRepository extends JpaRepository<StopEntity, UUID> {
}
