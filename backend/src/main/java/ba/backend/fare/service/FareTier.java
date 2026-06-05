package ba.backend.fare.service;

public record FareTier(
        int tier,
        double startKm,
        Double endKm,
        double multiplier
) {
    public boolean contains(double distanceKm) {
        return distanceKm >= startKm && (endKm == null || distanceKm < endKm);
    }
}
