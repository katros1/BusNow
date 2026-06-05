package ba.backend.fare.repository;

import ba.backend.fare.entity.FareSettingsEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FareSettingsRepository extends JpaRepository<FareSettingsEntity, UUID> {}
