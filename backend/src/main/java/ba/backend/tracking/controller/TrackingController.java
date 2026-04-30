package ba.backend.tracking.controller;

import ba.backend.bus.entity.BusEntity;
import ba.backend.bus.repository.BusRepository;
import ba.backend.route.entity.RouteEntity;
import ba.backend.tracking.dto.TrackingVehicleDto;
import ba.backend.trip.entity.TripEntity;
import ba.backend.trip.entity.TripStatus;
import ba.backend.trip.repository.TripRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tracking")
public class TrackingController {

    private final BusRepository busRepository;
    private final TripRepository tripRepository;

    public TrackingController(BusRepository busRepository, TripRepository tripRepository) {
        this.busRepository = busRepository;
        this.tripRepository = tripRepository;
    }

    /**
     * GET /api/v1/tracking/vehicles
     *
     * Returns all buses with their last known position, assigned route, and active trip info.
     * Use this as the initial snapshot for the tracking overview map.
     *
     * For live updates, subscribe to STOMP topic /topic/tracking.
     * For a single bus, subscribe to /topic/tracking/{busId}.
     */
    @GetMapping("/vehicles")
    public List<TrackingVehicleDto> getVehicles() {
        Map<UUID, TripEntity> activeByBusId = tripRepository.findByStatus(TripStatus.ACTIVE)
                .stream()
                .collect(Collectors.toMap(t -> t.getBus().getId(), t -> t, (a, b) -> a));

        return busRepository.findAll().stream()
                .map(bus -> toDto(bus, activeByBusId.get(bus.getId())))
                .toList();
    }

    private TrackingVehicleDto toDto(BusEntity bus, TripEntity trip) {
        UUID routeId = null;
        String routeName = null;
        String routeCode = null;
        String direction = null;
        UUID activeTripId = null;
        Integer passengersOnBoard = null;
        Integer availableSeats = null;

        if (trip != null) {
            RouteEntity route = trip.getRoute();
            routeId = route.getId();
            routeName = route.getName();
            routeCode = route.getRouteCode() != null ? route.getRouteCode().getCode() : null;
            direction = route.getDirection() != null ? route.getDirection().name() : null;
            activeTripId = trip.getId();
            passengersOnBoard = trip.getPassengersOnBoard();
            availableSeats = bus.getCapacity() != null
                    ? Math.max(0, bus.getCapacity() - trip.getPassengersOnBoard())
                    : null;
        } else if (bus.getRouteCode() != null) {
            routeCode = bus.getRouteCode().getCode();
        }

        return new TrackingVehicleDto(
                bus.getId(), bus.getPlateNumber(), bus.getModel(), bus.getCapacity(),
                bus.getCurrentLatitude(), bus.getCurrentLongitude(),
                routeId, routeName, routeCode, direction,
                activeTripId, passengersOnBoard, availableSeats);
    }
}
