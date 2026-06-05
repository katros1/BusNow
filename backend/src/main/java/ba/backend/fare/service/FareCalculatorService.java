package ba.backend.fare.service;

import ba.backend.fare.entity.FareSettingsEntity;
import ba.backend.fare.repository.FareSettingsRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FareCalculatorService {

    public static final List<FareTier> TIERS = List.of(
            new FareTier(1,  0.0,   1.5,  1.20),
            new FareTier(2,  1.5,   3.0,  1.00),
            new FareTier(3,  3.0,   6.0,  0.85),
            new FareTier(4,  6.0,  10.0,  0.70),
            new FareTier(5, 10.0,  15.0,  0.50),
            new FareTier(6, 15.0,  25.0,  0.25),
            new FareTier(7, 25.0,  null,  0.15)
    );

    private final FareSettingsRepository fareSettingsRepository;

    public FareCalculatorService(FareSettingsRepository fareSettingsRepository) {
        this.fareSettingsRepository = fareSettingsRepository;
    }

    @Transactional(readOnly = true)
    public FareSettingsEntity getSettings() {
        return fareSettingsRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Fare settings not initialised."));
    }

    @Transactional(readOnly = true)
    public double getCurrentBasePriceFrw() {
        return getSettings().getBasePriceFrw().doubleValue();
    }

    @Transactional
    public FareSettingsEntity updateBasePriceFrw(BigDecimal newPrice) {
        FareSettingsEntity settings = getSettings();
        settings.setBasePriceFrw(newPrice);
        return fareSettingsRepository.save(settings);
    }

    /** Returns the fare in RWF (rounded to whole francs). Formula: base × distance × multiplier. */
    public long calculateFare(double basePriceFrw, double distanceKm) {
        for (FareTier tier : TIERS) {
            if (tier.contains(distanceKm)) {
                return Math.round(basePriceFrw * distanceKm * tier.multiplier());
            }
        }
        // Fallback: last tier (25 km+)
        return Math.round(basePriceFrw * distanceKm * 0.15);
    }
}
