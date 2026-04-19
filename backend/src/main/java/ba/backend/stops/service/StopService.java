package ba.backend.stops.service;

import ba.backend.shared.dto.PolygonDto;
import ba.backend.shared.dto.PolygonResourceDto;
import ba.backend.shared.dto.PolygonUpdateDto;
import ba.backend.shared.exception.ResourceNotFoundException;
import ba.backend.shared.geo.PolygonGeometryMapper;
import ba.backend.stops.entity.StopEntity;
import ba.backend.stops.repository.StopRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StopService {

    private final StopRepository stopRepository;
    private final PolygonGeometryMapper polygonGeometryMapper;

    public StopService(StopRepository stopRepository, PolygonGeometryMapper polygonGeometryMapper) {
        this.stopRepository = stopRepository;
        this.polygonGeometryMapper = polygonGeometryMapper;
    }

    @Transactional
    public PolygonResourceDto create(PolygonDto request) {
        StopEntity entity = new StopEntity(request.name().trim(), polygonGeometryMapper.toPolygon(request));
        return toDto(stopRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public Page<PolygonResourceDto> list(String search, Pageable pageable) {
        Specification<StopEntity> specification = (root, query, criteriaBuilder) -> {
            if (search == null || search.isBlank()) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("name")),
                    "%" + search.trim().toLowerCase() + "%"
            );
        };
        return stopRepository.findAll(specification, pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public PolygonResourceDto get(UUID id) {
        StopEntity entity = stopRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Stop not found for id: " + id));
        return toDto(entity);
    }

    @Transactional
    public void delete(UUID id) {
        if (!stopRepository.existsById(id)) {
            throw new ResourceNotFoundException("Stop not found for id: " + id);
        }
        stopRepository.deleteById(id);
    }

    @Transactional
    public PolygonResourceDto update(UUID id, PolygonDto request) {
        StopEntity entity = stopRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Stop not found for id: " + id));
        entity.setName(request.name().trim());
        entity.setGeo(polygonGeometryMapper.toPolygon(request));
        return toDto(stopRepository.save(entity));
    }

    @Transactional
    public PolygonResourceDto patch(UUID id, PolygonUpdateDto request) {
        if (request.name() == null && request.coordinates() == null) {
            throw new IllegalArgumentException("At least one field must be provided for patch.");
        }

        StopEntity entity = stopRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Stop not found for id: " + id));

        if (request.name() != null) {
            if (request.name().isBlank()) {
                throw new IllegalArgumentException("Name must not be blank.");
            }
            entity.setName(request.name().trim());
        }

        if (request.coordinates() != null) {
            entity.setGeo(polygonGeometryMapper.toPolygon(request.coordinates()));
        }

        return toDto(stopRepository.save(entity));
    }

    private PolygonResourceDto toDto(StopEntity entity) {
        return new PolygonResourceDto(
                entity.getId(),
                entity.getName(),
                polygonGeometryMapper.toCoordinates(entity.getGeo()),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
