package ba.backend.routecode.dto;

import java.time.Instant;
import java.util.UUID;

public record RouteCodeResponseDto(
        UUID id,
        String code,
        RouteSummaryDto forwardRoute,
        RouteSummaryDto backwardRoute,
        Instant createdAt,
        Instant updatedAt
) {
}
