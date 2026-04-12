package ba.backend.driver.dto;

import ba.backend.driver.entity.DriverGender;
import ba.backend.driver.entity.LicenseCategory;

public record DriverUpdateDto(
        String firstName,
        String lastName,
        DriverGender gender,
        String phoneNumber,
        String licenseNumber,
        LicenseCategory licenseCategory
) {
}
