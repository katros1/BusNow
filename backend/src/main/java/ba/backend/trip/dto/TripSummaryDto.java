package ba.backend.trip.dto;

import ba.backend.trip.entity.TripEntity;
import ba.backend.trip.entity.TripStatus;
import java.time.Instant;
import java.util.UUID;

public record TripSummaryDto(
        UUID id,
        TripStatus status,
        Instant startedAt,
        Instant endedAt,
        UUID busId,
        String plateNumber,
        String model,
        UUID routeId,
        String routeName,
        String routeCode,
        String direction,
        int passengersOnBoard,
        Integer capacity,
        Integer availableSeats,
        Integer totalBoardings,
        Integer totalAlightings
) {
    public static TripSummaryDto from(TripEntity t) {
        Integer totalIn  = t.getFinalIn()  != null ? t.getFinalIn()  - t.getSnapshotIn()  : null;
        Integer totalOut = t.getFinalOut() != null ? t.getFinalOut() - t.getSnapshotOut() : null;
        Integer capacity = t.getBus().getCapacity();
        Integer available = capacity != null ? Math.max(0, capacity - t.getPassengersOnBoard()) : null;
        String code = t.getRoute().getRouteCode() != null ? t.getRoute().getRouteCode().getCode() : null;
        String dir  = t.getRoute().getDirection() != null ? t.getRoute().getDirection().name()  : null;

        return new TripSummaryDto(
                t.getId(), t.getStatus(), t.getStartedAt(), t.getEndedAt(),
                t.getBus().getId(), t.getBus().getPlateNumber(), t.getBus().getModel(),
                t.getRoute().getId(), t.getRoute().getName(), code, dir,
                t.getPassengersOnBoard(), capacity, available,
                totalIn, totalOut
        );
    }
}
