package ba.backend.trip.service;

import ba.backend.bus.entity.BusEntity;
import ba.backend.bus.repository.BusRepository;
import ba.backend.route.entity.RouteEntity;
import ba.backend.trip.entity.TripEntity;
import ba.backend.trip.entity.TripStatus;
import ba.backend.trip.entity.VehicleTripEntity;
import ba.backend.trip.repository.TripRepository;
import ba.backend.trip.repository.VehicleTripRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TripService {

    private static final Logger log = LoggerFactory.getLogger(TripService.class);

    private final TripRepository tripRepository;
    private final VehicleTripRepository vehicleTripRepository;
    private final BusRepository busRepository;

    public TripService(TripRepository tripRepository, 
                       VehicleTripRepository vehicleTripRepository,
                       BusRepository busRepository) {
        this.tripRepository = tripRepository;
        this.vehicleTripRepository = vehicleTripRepository;
        this.busRepository = busRepository;
    }

    @Transactional
    public TripEntity startTrip(BusEntity bus, RouteEntity route,
                                int snapshotIn, int snapshotOut, int passengersOnBoard) {
        
        // 0. Resilience: Adopt existing active trip to prevent uniqueness violations
        TripEntity existing = tripRepository.findByBusIdAndStatus(bus.getId(), TripStatus.ACTIVE).orElse(null);
        if (existing != null) {
            log.info("Adopting existing active trip {} for bus {}", existing.getId(), bus.getPlateNumber());
            
            // Ensure vehicle_trips also has an active record for this trip (sync check)
            if (vehicleTripRepository.findByBusIdAndStatus(bus.getId(), TripStatus.ACTIVE).isEmpty()) {
                log.warn("Active trip exists in iots_trip but missing in vehicle_trips. Creating history entry...");
                vehicleTripRepository.save(new VehicleTripEntity(bus, existing.getRoute(), TripStatus.ACTIVE, existing.getStartedAt(), 0, 0, 0));
            }
            return existing;
        }

        Instant now = Instant.now();
        log.info("Creating NEW trip for bus {} on route {}", bus.getPlateNumber(), route.getName());
        
        // 1. Save to vehicle_trips (History) FIRST
        // If this fails, the whole transaction rolls back, which is good.
        vehicleTripRepository.save(new VehicleTripEntity(bus, route, TripStatus.ACTIVE, now, 0, 0, 0));

        // 2. Save to iots_trip (Active tracking)
        return tripRepository.save(new TripEntity(bus, route, now, snapshotIn, snapshotOut, passengersOnBoard));
    }

    @Transactional
    public void completeTrip(UUID tripId, int finalIn, int finalOut) {
        Instant now = Instant.now();
        
        tripRepository.findById(tripId).ifPresentOrElse(trip -> {
            log.info("Completing trip {} for bus {}", tripId, trip.getBus().getPlateNumber());
            
            trip.complete(now, finalIn, finalOut);
            
            // Persist the last completed route on the bus
            BusEntity bus = trip.getBus();
            bus.setLastCompletedRouteId(trip.getRoute().getId());
            busRepository.save(bus);
            
            // Sync with history table
            vehicleTripRepository.findByBusIdAndStatus(bus.getId(), TripStatus.ACTIVE)
                    .ifPresentOrElse(history -> {
                        int tripIn  = Math.max(0, finalIn  - trip.getSnapshotIn());
                        int tripOut = Math.max(0, finalOut - trip.getSnapshotOut());
                        int onBoard = Math.max(0, tripIn - tripOut);
                        history.complete(now, tripIn, tripOut, onBoard);
                        log.info("History record completed for trip {}", tripId);
                    }, () -> log.error("No active history record found to complete for bus {}", bus.getPlateNumber()));
        }, () -> log.error("Attempted to complete non-existent trip {}", tripId));
    }

    @Transactional
    public void updatePassengersOnBoard(UUID tripId, int onBoard) {
        tripRepository.findById(tripId).ifPresent(trip -> {
            trip.updatePassengersOnBoard(onBoard);
            
            // Sync with history table
            vehicleTripRepository.findByBusIdAndStatus(trip.getBus().getId(), TripStatus.ACTIVE)
                    .ifPresent(history -> {
                        history.setOnBoard(onBoard);
                    });
        });
    }
}
