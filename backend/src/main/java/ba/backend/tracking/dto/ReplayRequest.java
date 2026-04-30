package ba.backend.tracking.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ReplayRequest(
        @NotNull UUID tripId,
        Double speedMultiplier  // null → defaults to 5.0× in the service
) {}
