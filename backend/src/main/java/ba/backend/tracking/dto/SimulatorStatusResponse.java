package ba.backend.tracking.dto;

public record SimulatorStatusResponse(
        String imei,
        String status,
        double progressPercent,
        long   ticksCompleted,
        int    totalTicks,
        double currentLat,
        double currentLon,
        double currentHeading,
        double speedKmh,
        int    intervalS,
        double routeDistanceKm,
        int    estimatedOnBoard,   // cumulativePaxIn − cumulativePaxOut for current leg
        int    busCapacity,        // from busnow_bus.bus_capacity
        int    tripCount,          // how many legs completed so far (0 = first leg running)
        int    cumulativePaxIn,    // total boardings since simulator started (cumulative)
        int    cumulativePaxOut    // total alightings since simulator started (cumulative)
) {}
