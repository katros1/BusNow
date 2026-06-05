package ba.backend.fare.controller;

import ba.backend.fare.dto.FareSettingsResponseDto;
import ba.backend.fare.dto.FareSettingsUpdateDto;
import ba.backend.fare.dto.FareTierDto;
import ba.backend.fare.entity.FareSettingsEntity;
import ba.backend.fare.service.FareCalculatorService;
import ba.backend.fare.service.FareTier;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fare-settings")
public class FareSettingsController {

    private final FareCalculatorService fareCalculatorService;

    public FareSettingsController(FareCalculatorService fareCalculatorService) {
        this.fareCalculatorService = fareCalculatorService;
    }

    @GetMapping
    public FareSettingsResponseDto get() {
        FareSettingsEntity settings = fareCalculatorService.getSettings();
        double base = settings.getBasePriceFrw().doubleValue();
        return toDto(settings, base);
    }

    @PutMapping
    public FareSettingsResponseDto update(@Valid @RequestBody FareSettingsUpdateDto dto) {
        FareSettingsEntity updated = fareCalculatorService.updateBasePriceFrw(dto.basePriceFrw());
        double base = updated.getBasePriceFrw().doubleValue();
        return toDto(updated, base);
    }

    private FareSettingsResponseDto toDto(FareSettingsEntity settings, double base) {
        List<FareTierDto> tiers = FareCalculatorService.TIERS.stream()
                .map(t -> new FareTierDto(
                        t.tier(),
                        t.startKm(),
                        t.endKm(),
                        t.multiplier(),
                        fareCalculatorService.calculateFare(base, midpoint(t))
                ))
                .toList();
        return new FareSettingsResponseDto(base, settings.getUpdatedAt(), tiers);
    }

    /** Representative distance for showing an example fare for each tier. */
    private double midpoint(FareTier tier) {
        if (tier.endKm() == null) return tier.startKm() + 5.0;
        return (tier.startKm() + tier.endKm()) / 2.0;
    }
}
