package ba.backend.fare.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record FareSettingsUpdateDto(
        @NotNull
        @DecimalMin("1.00")
        BigDecimal basePriceFrw
) {}
