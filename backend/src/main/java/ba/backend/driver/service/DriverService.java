package ba.backend.driver.service;

import ba.backend.driver.dto.DriverCreateDto;
import ba.backend.driver.dto.DriverResponseDto;
import ba.backend.driver.dto.DriverUpdateDto;
import ba.backend.driver.entity.DriverGender;
import ba.backend.driver.entity.DriverEntity;
import ba.backend.driver.entity.LicenseCategory;
import ba.backend.driver.repository.DriverRepository;
import ba.backend.shared.exception.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DriverService {

    private final DriverRepository driverRepository;

    public DriverService(DriverRepository driverRepository) {
        this.driverRepository = driverRepository;
    }

    @Transactional
    public DriverResponseDto create(DriverCreateDto request) {
        DriverEntity entity = new DriverEntity(
                normalizedRequired(request.firstName(), "firstName"),
                normalizedRequired(request.lastName(), "lastName"),
                request.gender(),
                normalizedRequired(request.phoneNumber(), "phoneNumber"),
                normalizedRequired(request.licenseNumber(), "licenseNumber"),
                request.licenseCategory()
        );
        return toDto(driverRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public Page<DriverResponseDto> list(String search, DriverGender gender, LicenseCategory licenseCategory, Pageable pageable) {
        Specification<DriverEntity> specification = (root, query, criteriaBuilder) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();

            if (search != null && !search.isBlank()) {
                String likeValue = "%" + search.trim().toLowerCase() + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("firstName")), likeValue),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("lastName")), likeValue),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("phoneNumber")), likeValue),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("licenseNumber")), likeValue)
                ));
            }
            if (gender != null) {
                predicates.add(criteriaBuilder.equal(root.get("gender"), gender));
            }
            if (licenseCategory != null) {
                predicates.add(criteriaBuilder.equal(root.get("licenseCategory"), licenseCategory));
            }
            return criteriaBuilder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        return driverRepository.findAll(specification, pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public DriverResponseDto get(UUID id) {
        return toDto(findDriver(id));
    }

    @Transactional
    public DriverResponseDto update(UUID id, DriverCreateDto request) {
        DriverEntity entity = findDriver(id);
        entity.setFirstName(normalizedRequired(request.firstName(), "firstName"));
        entity.setLastName(normalizedRequired(request.lastName(), "lastName"));
        entity.setGender(request.gender());
        entity.setPhoneNumber(normalizedRequired(request.phoneNumber(), "phoneNumber"));
        entity.setLicenseNumber(normalizedRequired(request.licenseNumber(), "licenseNumber"));
        entity.setLicenseCategory(request.licenseCategory());
        return toDto(driverRepository.save(entity));
    }

    @Transactional
    public DriverResponseDto patch(UUID id, DriverUpdateDto request) {
        if (request.firstName() == null
                && request.lastName() == null
                && request.gender() == null
                && request.phoneNumber() == null
                && request.licenseNumber() == null
                && request.licenseCategory() == null) {
            throw new IllegalArgumentException("At least one field must be provided for patch.");
        }

        DriverEntity entity = findDriver(id);
        if (request.firstName() != null) {
            entity.setFirstName(normalizedRequired(request.firstName(), "firstName"));
        }
        if (request.lastName() != null) {
            entity.setLastName(normalizedRequired(request.lastName(), "lastName"));
        }
        if (request.gender() != null) {
            entity.setGender(request.gender());
        }
        if (request.phoneNumber() != null) {
            entity.setPhoneNumber(normalizedRequired(request.phoneNumber(), "phoneNumber"));
        }
        if (request.licenseNumber() != null) {
            entity.setLicenseNumber(normalizedRequired(request.licenseNumber(), "licenseNumber"));
        }
        if (request.licenseCategory() != null) {
            entity.setLicenseCategory(request.licenseCategory());
        }
        return toDto(driverRepository.save(entity));
    }

    @Transactional
    public void delete(UUID id) {
        if (!driverRepository.existsById(id)) {
            throw new ResourceNotFoundException("Driver not found for id: " + id);
        }
        driverRepository.deleteById(id);
    }

    private DriverEntity findDriver(UUID id) {
        return driverRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found for id: " + id));
    }

    private String normalizedRequired(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank.");
        }
        return value.trim();
    }

    private DriverResponseDto toDto(DriverEntity entity) {
        return new DriverResponseDto(
                entity.getId(),
                entity.getFirstName(),
                entity.getLastName(),
                entity.getGender(),
                entity.getPhoneNumber(),
                entity.getLicenseNumber(),
                entity.getLicenseCategory(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
