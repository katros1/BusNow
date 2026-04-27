package ba.backend.trip.controller;

import ba.backend.shared.exception.ResourceNotFoundException;
import ba.backend.tracking.repository.VehicleLocationRepository;
import ba.backend.trip.dto.LocationPointDto;
import ba.backend.trip.dto.TripSummaryDto;
import ba.backend.trip.entity.TripEntity;
import ba.backend.trip.entity.TripStatus;
import ba.backend.trip.repository.TripRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trips")
public class TripController {

    private final TripRepository tripRepository;
    private final VehicleLocationRepository locationRepository;

    public TripController(TripRepository tripRepository,
                          VehicleLocationRepository locationRepository) {
        this.tripRepository     = tripRepository;
        this.locationRepository = locationRepository;
    }

    @GetMapping
    public Page<TripSummaryDto> list(
            @RequestParam(required = false) UUID busId,
            @RequestParam(required = false) TripStatus status,
            @PageableDefault(size = 20, sort = "startedAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Specification<TripEntity> spec = buildSpec(busId, status);
        return tripRepository.findAll(spec, pageable).map(TripSummaryDto::from);
    }

    @GetMapping("/{id}")
    public TripSummaryDto get(@PathVariable UUID id) {
        return tripRepository.findById(id)
                .map(TripSummaryDto::from)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found: " + id));
    }

    @GetMapping("/{id}/locations")
    public List<LocationPointDto> locations(@PathVariable UUID id) {
        if (!tripRepository.existsById(id)) throw new ResourceNotFoundException("Trip not found: " + id);
        return locationRepository.findByTripIdOrderByRecordedAtAsc(id)
                .stream().map(LocationPointDto::from).toList();
    }

    private Specification<TripEntity> buildSpec(UUID busId, TripStatus status) {
        Specification<TripEntity> spec = (r, q, cb) -> null;
        if (busId  != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("bus").get("id"), busId));
        if (status != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("status"), status));
        return spec;
    }
}
