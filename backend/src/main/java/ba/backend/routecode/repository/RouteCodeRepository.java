package ba.backend.routecode.repository;

import ba.backend.routecode.entity.RouteCodeEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteCodeRepository extends JpaRepository<RouteCodeEntity, UUID> {
}
