package ba.backend.routecode.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RouteCodeCreateDto(
        @NotBlank String code,
        @NotNull UUID forwardRouteId,
        @NotNull UUID backwardRouteId
) {
}
