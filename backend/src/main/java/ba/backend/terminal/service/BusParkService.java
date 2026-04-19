package ba.backend.terminal.service;

import ba.backend.shared.dto.PolygonDto;
import ba.backend.shared.dto.PolygonResourceDto;
import ba.backend.shared.dto.PolygonUpdateDto;
import ba.backend.shared.exception.ResourceNotFoundException;
import ba.backend.shared.geo.PolygonGeometryMapper;
import ba.backend.terminal.entity.BusParkEntity;
import ba.backend.terminal.repository.BusParkRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BusParkService {

    private final BusParkRepository busParkRepository;
    private final PolygonGeometryMapper polygonGeometryMapper;

    public BusParkService(BusParkRepository busParkRepository, PolygonGeometryMapper polygonGeometryMapper) {
        this.busParkRepository = busParkRepository;
        this.polygonGeometryMapper = polygonGeometryMapper;
    }

    @Transactional
    public PolygonResourceDto create(PolygonDto request) {
        BusParkEntity entity = new BusParkEntity(request.name().trim(), polygonGeometryMapper.toPolygon(request));
        return toDto(busParkRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public Page<PolygonResourceDto> list(String search, Pageable pageable) {
        Specification<BusParkEntity> specification = (root, query, criteriaBuilder) -> {
            if (search == null || search.isBlank()) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("name")),
                    "%" + search.trim().toLowerCase() + "%"
            );
        };
        return busParkRepository.findAll(specification, pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public PolygonResourceDto get(UUID id) {
        BusParkEntity entity = busParkRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Terminal not found for id: " + id));
        return toDto(entity);
    }

    @Transactional
    public void delete(UUID id) {
        if (!busParkRepository.existsById(id)) {
            throw new ResourceNotFoundException("Terminal not found for id: " + id);
        }
        busParkRepository.deleteById(id);
    }

    @Transactional
    public PolygonResourceDto update(UUID id, PolygonDto request) {
        BusParkEntity entity = busParkRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Terminal not found for id: " + id));
        entity.setName(request.name().trim());
        entity.setPolygon(polygonGeometryMapper.toPolygon(request));
        return toDto(busParkRepository.save(entity));
    }

    @Transactional
    public PolygonResourceDto patch(UUID id, PolygonUpdateDto request) {
        if (request.name() == null && request.coordinates() == null) {
            throw new IllegalArgumentException("At least one field must be provided for patch.");
        }

        BusParkEntity entity = busParkRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Terminal not found for id: " + id));

        if (request.name() != null) {
            if (request.name().isBlank()) {
                throw new IllegalArgumentException("Name must not be blank.");
            }
            entity.setName(request.name().trim());
        }

        if (request.coordinates() != null) {
            entity.setPolygon(polygonGeometryMapper.toPolygon(request.coordinates()));
        }

        return toDto(busParkRepository.save(entity));
    }

    private PolygonResourceDto toDto(BusParkEntity entity) {
        return new PolygonResourceDto(
                entity.getId(),
                entity.getName(),
                polygonGeometryMapper.toCoordinates(entity.getPolygon()),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
