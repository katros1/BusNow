package ba.backend.trip.controller;

import ba.backend.bus.entity.BusEntity;
import ba.backend.bus.repository.BusRepository;
import ba.backend.route.entity.RouteEntity;
import ba.backend.route.repository.RouteRepository;
import ba.backend.shared.exception.ResourceNotFoundException;
import ba.backend.tracking.repository.VehicleLocationRepository;
import ba.backend.trip.dto.LocationPointDto;
import ba.backend.trip.dto.TripSummaryDto;
import ba.backend.trip.entity.TripEntity;
import ba.backend.trip.entity.TripStatus;
import ba.backend.trip.repository.TripRepository;
import ba.backend.trip.service.TripService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trips")
public class TripController {

    private final TripRepository tripRepository;
    private final VehicleLocationRepository locationRepository;
    private final TripService tripService;
    private final BusRepository busRepository;
    private final RouteRepository routeRepository;

    public TripController(TripRepository tripRepository,
                          VehicleLocationRepository locationRepository,
                          TripService tripService,
                          BusRepository busRepository,
                          RouteRepository routeRepository) {
        this.tripRepository     = tripRepository;
        this.locationRepository = locationRepository;
        this.tripService        = tripService;
        this.busRepository      = busRepository;
        this.routeRepository    = routeRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public Page<TripSummaryDto> list(
            @RequestParam(required = false) UUID busId,
            @RequestParam(required = false) TripStatus status,
            @PageableDefault(size = 20, sort = "startedAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<TripEntity> page;
        if (busId != null && status != null) {
            page = tripRepository.findByBusIdAndStatus(busId, status, pageable);
        } else if (busId != null) {
            page = tripRepository.findByBusId(busId, pageable);
        } else if (status != null) {
            page = tripRepository.findByStatus(status, pageable);
        } else {
            page = tripRepository.findAll(pageable);
        }
        return page.map(TripSummaryDto::from);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
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

    /**
     * Manually start a trip for a bus on a given route.
     * Use this when the geofence-based auto-start cannot fire (e.g. dev testing,
     * missing bus-park polygon, or simulator started from outside the start park).
     *
     * POST /api/v1/trips/start?busId=&routeId=
     */
    @PostMapping("/start")
    @Transactional
    public ResponseEntity<?> manualStart(
            @RequestParam UUID busId,
            @RequestParam UUID routeId) {

        BusEntity bus = busRepository.findById(busId)
                .orElseThrow(() -> new ResourceNotFoundException("Bus not found: " + busId));
        RouteEntity route = routeRepository.findById(routeId)
                .orElseThrow(() -> new ResourceNotFoundException("Route not found: " + routeId));

        try {
            TripEntity trip = tripService.startTrip(bus, route, 0, 0, 0);
            return ResponseEntity.ok(TripSummaryDto.from(trip));
        } catch (Exception e) {
            return ResponseEntity.status(422)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Manually complete an active trip.
     * POST /api/v1/trips/{id}/complete
     */
    @PostMapping("/{id}/complete")
    @Transactional
    public ResponseEntity<?> manualComplete(@PathVariable UUID id) {
        TripEntity trip = tripRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found: " + id));
        if (trip.getStatus() != TripStatus.ACTIVE) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Trip is not active"));
        }
        tripService.completeTrip(id, trip.getSnapshotIn(), trip.getSnapshotOut());
        return ResponseEntity.ok(Map.of("message", "Trip completed"));
    }
}
