package ba.backend.driver.entity;

import ba.backend.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "iots_driver")
public class DriverEntity extends BaseEntity {

    @Column(name = "dr_first_name", nullable = false)
    private String firstName;

    @Column(name = "dr_last_name", nullable = false)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(name = "dr_gender", nullable = false)
    private DriverGender gender;

    @Column(name = "dr_phone_number", nullable = false, unique = true)
    private String phoneNumber;

    @Column(name = "dr_license_number", nullable = false, unique = true)
    private String licenseNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "dr_license_category", nullable = false)
    private LicenseCategory licenseCategory;

    protected DriverEntity() {
    }

    public DriverEntity(
            String firstName,
            String lastName,
            DriverGender gender,
            String phoneNumber,
            String licenseNumber,
            LicenseCategory licenseCategory
    ) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.gender = gender;
        this.phoneNumber = phoneNumber;
        this.licenseNumber = licenseNumber;
        this.licenseCategory = licenseCategory;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public DriverGender getGender() {
        return gender;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getLicenseNumber() {
        return licenseNumber;
    }

    public LicenseCategory getLicenseCategory() {
        return licenseCategory;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public void setGender(DriverGender gender) {
        this.gender = gender;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public void setLicenseNumber(String licenseNumber) {
        this.licenseNumber = licenseNumber;
    }

    public void setLicenseCategory(LicenseCategory licenseCategory) {
        this.licenseCategory = licenseCategory;
    }
}
