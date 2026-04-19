package ba.backend.plan.repository;

import java.util.UUID;

public interface PlanNearestStopProjection {
    UUID getStopId();

    String getStopName();

    Double getLongitude();

    Double getLatitude();

    Double getDistanceKm();
}
