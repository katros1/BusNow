package ba.backend.plan.repository;

import java.util.UUID;

public interface PlanRoutePointProjection {
    UUID getRouteId();

    UUID getPointId();

    String getPointName();

    String getPointType();

    Integer getPointSequence();

    Double getLongitude();

    Double getLatitude();

    Double getWalkToBoardingKm();

    Double getWalkToDestinationKm();
}
