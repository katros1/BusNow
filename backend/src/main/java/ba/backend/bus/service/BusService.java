package ba.backend.bus.service;

import ba.backend.bus.dto.BusCreateDto;
import ba.backend.bus.dto.BusDriverRefDto;
import ba.backend.bus.dto.BusResponseDto;
import ba.backend.bus.dto.BusRouteCodeRefDto;
import ba.backend.bus.dto.BusUpdateDto;
import ba.backend.bus.entity.BusEntity;
import ba.backend.bus.repository.BusRepository;
import ba.backend.driver.entity.DriverEntity;
import ba.backend.driver.repository.DriverRepository;
import ba.backend.routecode.entity.RouteCodeEntity;
import ba.backend.routecode.repository.RouteCodeRepository;
import ba.backend.shared.exception.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BusService {

    private final BusRepository busRepository;
    private final DriverRepository driverRepository;
    private final RouteCodeRepository routeCodeRepository;

    public BusService(BusRepository busRepository, DriverRepository driverRepository, RouteCodeRepository routeCodeRepository) {
        this.busRepository = busRepository;
        this.driverRepository = driverRepository;
        this.routeCodeRepository = routeCodeRepository;
    }

    @Transactional
    public BusResponseDto create(BusCreateDto request) {
        validateCoordinates(request.currentLatitude(), request.currentLongitude());
        BusEntity entity = new BusEntity(
                normalizedRequired(request.plateNumber(), "plateNumber"),
                normalizedRequired(request.gpsImei(), "gpsImei"),
                normalizeNullable(request.model()),
                request.capacity(),
                request.currentLatitude(),
                request.currentLongitude(),
                findDriverNullable(request.currentDriverId()),
                findRouteCodeNullable(request.routeCodeId())
        );
        return toDto(busRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<BusResponseDto> list() {
        return busRepository.findAll().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public BusResponseDto get(UUID id) {
        return toDto(findBus(id));
    }

    @Transactional
    public BusResponseDto update(UUID id, BusCreateDto request) {
        validateCoordinates(request.currentLatitude(), request.currentLongitude());
        BusEntity entity = findBus(id);
        entity.setPlateNumber(normalizedRequired(request.plateNumber(), "plateNumber"));
        entity.setGpsImei(normalizedRequired(request.gpsImei(), "gpsImei"));
        entity.setModel(normalizeNullable(request.model()));
        entity.setCapacity(request.capacity());
        entity.setCurrentLatitude(request.currentLatitude());
        entity.setCurrentLongitude(request.currentLongitude());
        entity.setCurrentDriver(findDriverNullable(request.currentDriverId()));
        entity.setRouteCode(findRouteCodeNullable(request.routeCodeId()));
        return toDto(busRepository.save(entity));
    }

    @Transactional
    public BusResponseDto patch(UUID id, BusUpdateDto request) {
        if (request.plateNumber() == null
                && request.gpsImei() == null
                && request.model() == null
                && request.capacity() == null
                && request.currentLatitude() == null
                && request.currentLongitude() == null
                && request.currentDriverId() == null
                && request.routeCodeId() == null) {
            throw new IllegalArgumentException("At least one field must be provided for patch.");
        }

        validateCoordinates(request.currentLatitude(), request.currentLongitude());
        BusEntity entity = findBus(id);

        if (request.plateNumber() != null) {
            entity.setPlateNumber(normalizedRequired(request.plateNumber(), "plateNumber"));
        }
        if (request.gpsImei() != null) {
            entity.setGpsImei(normalizedRequired(request.gpsImei(), "gpsImei"));
        }
        if (request.model() != null) {
            entity.setModel(normalizeNullable(request.model()));
        }
        if (request.capacity() != null) {
            entity.setCapacity(request.capacity());
        }
        if (request.currentLatitude() != null) {
            entity.setCurrentLatitude(request.currentLatitude());
        }
        if (request.currentLongitude() != null) {
            entity.setCurrentLongitude(request.currentLongitude());
        }
        if (request.currentDriverId() != null) {
            entity.setCurrentDriver(findDriverNullable(request.currentDriverId()));
        }
        if (request.routeCodeId() != null) {
            entity.setRouteCode(findRouteCodeNullable(request.routeCodeId()));
        }

        return toDto(busRepository.save(entity));
    }

    @Transactional
    public void delete(UUID id) {
        if (!busRepository.existsById(id)) {
            throw new ResourceNotFoundException("Bus not found for id: " + id);
        }
        busRepository.deleteById(id);
    }

    private BusEntity findBus(UUID id) {
        return busRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bus not found for id: " + id));
    }

    private DriverEntity findDriverNullable(UUID id) {
        if (id == null) {
            return null;
        }
        return driverRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Driver not found for id: " + id));
    }

    private RouteCodeEntity findRouteCodeNullable(UUID id) {
        if (id == null) {
            return null;
        }
        return routeCodeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Route code not found for id: " + id));
    }

    private String normalizedRequired(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank.");
        }
        return value.trim();
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        return value.isBlank() ? null : value.trim();
    }

    private void validateCoordinates(Double latitude, Double longitude) {
        if ((latitude == null) != (longitude == null)) {
            throw new IllegalArgumentException("Both currentLatitude and currentLongitude must be provided together.");
        }
    }

    private BusResponseDto toDto(BusEntity entity) {
        BusDriverRefDto driver = null;
        if (entity.getCurrentDriver() != null) {
            driver = new BusDriverRefDto(
                    entity.getCurrentDriver().getId(),
                    entity.getCurrentDriver().getFirstName() + " " + entity.getCurrentDriver().getLastName()
            );
        }

        BusRouteCodeRefDto routeCode = null;
        if (entity.getRouteCode() != null) {
            routeCode = new BusRouteCodeRefDto(entity.getRouteCode().getId(), entity.getRouteCode().getCode());
        }

        return new BusResponseDto(
                entity.getId(),
                entity.getPlateNumber(),
                entity.getGpsImei(),
                entity.getModel(),
                entity.getCapacity(),
                entity.getCurrentLatitude(),
                entity.getCurrentLongitude(),
                driver,
                routeCode,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
