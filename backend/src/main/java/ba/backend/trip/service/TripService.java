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
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
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

    /**
     * On startup, cancel all duplicate ACTIVE trips — keep only the most recent per bus.
     * Prevents NonUniqueResultException from dirty data created before lifecycle fixes.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void cleanupDuplicateActiveTrips() {
        List<TripEntity> allActive = tripRepository.findByStatus(TripStatus.ACTIVE);
        allActive.stream()
                .collect(java.util.stream.Collectors.groupingBy(t -> t.getBus().getId()))
                .forEach((busId, trips) -> {
                    if (trips.size() <= 1) return;
                    trips.sort(Comparator.comparing(TripEntity::getStartedAt).reversed());
                    List<TripEntity> duplicates = trips.subList(1, trips.size());
                    log.warn("Cancelling {} duplicate ACTIVE trip(s) for bus {} — keeping trip {}",
                            duplicates.size(), busId, trips.get(0).getId());
                    Instant now = Instant.now();
                    for (TripEntity dup : duplicates) {
                        dup.complete(now, dup.getSnapshotIn(), dup.getSnapshotOut());
                        tripRepository.save(dup);
                    }
                });

        List<VehicleTripEntity> allActiveVt = vehicleTripRepository.findAll().stream()
                .filter(vt -> vt.getStatus() == TripStatus.ACTIVE)
                .toList();
        allActiveVt.stream()
                .collect(java.util.stream.Collectors.groupingBy(vt -> vt.getBus().getId()))
                .forEach((busId, vts) -> {
                    if (vts.size() <= 1) return;
                    vts.sort(Comparator.comparing(VehicleTripEntity::getStartedAt).reversed());
                    List<VehicleTripEntity> duplicates = vts.subList(1, vts.size());
                    log.warn("Cancelling {} duplicate ACTIVE vehicle_trips for bus {}", duplicates.size(), busId);
                    Instant now = Instant.now();
                    for (VehicleTripEntity dup : duplicates) {
                        dup.complete(now, 0, 0, 0);
                        vehicleTripRepository.save(dup);
                    }
                });
    }

    @Transactional
    public TripEntity startTrip(BusEntity bus, RouteEntity route,
                                int snapshotIn, int snapshotOut, int passengersOnBoard) {

        TripEntity existing = tripRepository.findFirstByBusIdAndStatusOrderByStartedAtDesc(bus.getId(), TripStatus.ACTIVE).orElse(null);
        if (existing != null) {
            log.info("Adopting existing active trip {} for bus {}", existing.getId(), bus.getPlateNumber());
            if (vehicleTripRepository.findFirstByBusIdAndStatusOrderByStartedAtDesc(bus.getId(), TripStatus.ACTIVE).isEmpty()) {
                log.warn("Active trip exists in iots_trip but missing in vehicle_trips. Synchronizing history...");
                vehicleTripRepository.save(new VehicleTripEntity(bus, existing.getRoute(), TripStatus.ACTIVE, existing.getStartedAt(), 0, 0, 0));
            }
            return existing;
        }

        Instant now = Instant.now();
        log.info("Creating NEW trip record for bus {} on route {}", bus.getPlateNumber(), route.getName());

        vehicleTripRepository.save(new VehicleTripEntity(bus, route, TripStatus.ACTIVE, now, 0, 0, 0));
        return tripRepository.save(new TripEntity(bus, route, now, snapshotIn, snapshotOut, passengersOnBoard));
    }

    @Transactional
    public void completeTrip(UUID tripId, int finalIn, int finalOut) {
        Instant now = Instant.now();

        tripRepository.findById(tripId).ifPresentOrElse(trip -> {
            log.info("COMPLETING trip {} (bus {})", tripId, trip.getBus().getPlateNumber());

            trip.complete(now, finalIn, finalOut);
            tripRepository.save(trip);

            BusEntity bus = trip.getBus();
            bus.setLastCompletedRouteId(trip.getRoute().getId());
            busRepository.save(bus);

            vehicleTripRepository.findFirstByBusIdAndStatusOrderByStartedAtDesc(bus.getId(), TripStatus.ACTIVE)
                    .ifPresentOrElse(history -> {
                        int tripIn  = Math.max(0, finalIn  - trip.getSnapshotIn());
                        int tripOut = Math.max(0, finalOut - trip.getSnapshotOut());
                        int onBoard = Math.max(0, tripIn - tripOut);
                        history.complete(now, tripIn, tripOut, onBoard);
                        vehicleTripRepository.save(history);
                        log.info("History record successfully COMPLETED for bus {}", bus.getPlateNumber());
                    }, () -> log.error("SYNC ERROR: No active record found in vehicle_trips for bus {}", bus.getPlateNumber()));
        }, () -> log.error("ERROR: Attempted to complete non-existent trip ID: {}", tripId));
    }

    @Transactional
    public void updatePassengersOnBoard(UUID tripId, int onBoard) {
        tripRepository.findById(tripId).ifPresent(trip -> {
            trip.updatePassengersOnBoard(onBoard);
            tripRepository.save(trip);

            vehicleTripRepository.findFirstByBusIdAndStatusOrderByStartedAtDesc(trip.getBus().getId(), TripStatus.ACTIVE)
                    .ifPresent(history -> {
                        history.setOnBoard(onBoard);
                        vehicleTripRepository.save(history);
                    });
        });
    }
}
