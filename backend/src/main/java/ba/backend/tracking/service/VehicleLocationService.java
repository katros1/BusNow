package ba.backend.tracking.service;

import ba.backend.bus.repository.BusRepository;
import ba.backend.tracking.entity.VehicleLocationEntity;
import ba.backend.tracking.repository.VehicleLocationRepository;
import ba.backend.trip.repository.TripRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VehicleLocationService {

    private final VehicleLocationRepository locationRepository;
    private final BusRepository busRepository;
    private final TripRepository tripRepository;

    public VehicleLocationService(VehicleLocationRepository locationRepository,
                                  BusRepository busRepository,
                                  TripRepository tripRepository) {
        this.locationRepository = locationRepository;
        this.busRepository      = busRepository;
        this.tripRepository     = tripRepository;
    }

    @Transactional
    public void record(UUID busId, UUID tripId, double latitude, double longitude,
                       Double speedKmh, Double headingDeg, Integer passengersOnBoard,
                       Instant recordedAt) {
        locationRepository.save(new VehicleLocationEntity(
                busRepository.getReferenceById(busId),
                tripId != null ? tripRepository.getReferenceById(tripId) : null,
                latitude, longitude,
                speedKmh, headingDeg,
                passengersOnBoard,
                recordedAt
        ));
    }
}
