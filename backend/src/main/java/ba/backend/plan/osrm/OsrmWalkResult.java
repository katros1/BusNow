package ba.backend.plan.osrm;

public record OsrmWalkResult(double distanceMeters, double durationSeconds) {

    public double distanceKm() {
        return distanceMeters / 1000.0;
    }

    public int durationMinutes() {
        return (int) Math.ceil(durationSeconds / 60.0);
    }
}
