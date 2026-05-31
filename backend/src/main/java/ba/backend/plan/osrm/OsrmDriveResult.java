package ba.backend.plan.osrm;

import java.util.List;

/**
 * Driving route returned by OSRM.
 * Waypoints are ordered [lat, lon] (WGS-84), converted from OSRM's native [lng, lat].
 */
public record OsrmDriveResult(
        double distanceMeters,
        double durationSeconds,
        List<double[]> waypoints
) {}
