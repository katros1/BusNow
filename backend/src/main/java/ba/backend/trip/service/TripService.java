package ba.backend.trip.service;

import ba.backend.bus.entity.BusEntity;
import ba.backend.route.entity.RouteEntity;
import ba.backend.trip.entity.TripEntity;
import ba.backend.trip.repository.TripRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TripService {

    private final TripRepository tripRepository;

    public TripService(TripRepository tripRepository) {
        this.tripRepository = tripRepository;
    }

    @Transactional
    public TripEntity startTrip(BusEntity bus, RouteEntity route,
                                int snapshotIn, int snapshotOut, int passengersOnBoard) {
        return tripRepository.save(
                new TripEntity(bus, route, Instant.now(), snapshotIn, snapshotOut, passengersOnBoard));
    }

    @Transactional
    public void completeTrip(UUID tripId, int finalIn, int finalOut) {
        tripRepository.findById(tripId)
                .ifPresent(trip -> trip.complete(Instant.now(), finalIn, finalOut));
    }

    @Transactional
    public void updatePassengersOnBoard(UUID tripId, int remaining) {
        tripRepository.findById(tripId).ifPresent(trip -> trip.updatePassengersOnBoard(remaining));
    }
}
