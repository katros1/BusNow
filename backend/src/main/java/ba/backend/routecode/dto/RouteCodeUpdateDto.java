package ba.backend.routecode.dto;

import java.util.UUID;

public record RouteCodeUpdateDto(
        String code,
        UUID forwardRouteId,
        UUID backwardRouteId
) {
}
