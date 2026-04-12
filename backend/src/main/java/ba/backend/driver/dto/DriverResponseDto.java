package ba.backend.driver.dto;

import ba.backend.driver.entity.DriverGender;
import ba.backend.driver.entity.LicenseCategory;
import java.time.Instant;
import java.util.UUID;

public record DriverResponseDto(
        UUID id,
        String firstName,
        String lastName,
        DriverGender gender,
        String phoneNumber,
        String licenseNumber,
        LicenseCategory licenseCategory,
        Instant createdAt,
        Instant updatedAt
) {
}
