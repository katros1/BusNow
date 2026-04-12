package ba.backend.driver.dto;

import ba.backend.driver.entity.DriverGender;
import ba.backend.driver.entity.LicenseCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DriverCreateDto(
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotNull DriverGender gender,
        @NotBlank String phoneNumber,
        @NotBlank String licenseNumber,
        @NotNull LicenseCategory licenseCategory
) {
}
