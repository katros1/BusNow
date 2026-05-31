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
        double routeDistanceKm
) {}
