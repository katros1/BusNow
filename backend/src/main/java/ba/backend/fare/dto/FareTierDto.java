package ba.backend.fare.dto;

public record FareTierDto(
        int tier,
        double startKm,
        Double endKm,
        double multiplier,
        long exampleFare
) {}
