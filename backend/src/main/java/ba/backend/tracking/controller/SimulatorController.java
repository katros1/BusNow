package ba.backend.tracking.controller;

import ba.backend.tracking.dto.ReplayRequest;
import ba.backend.tracking.dto.ReplayTripDto;
import ba.backend.tracking.dto.SimulateRequest;
import ba.backend.tracking.service.SimulatorService;
import ba.backend.vehicledata.service.VehicleDataService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/simulator")
public class SimulatorController {

    private final VehicleDataService vehicleDataService;
    private final SimulatorService simulatorService;

    public SimulatorController(VehicleDataService vehicleDataService, SimulatorService simulatorService) {
        this.vehicleDataService = vehicleDataService;
        this.simulatorService   = simulatorService;
    }

    /**
     * POST /api/v1/simulator/frame
     *
     * Injects a single GPS frame exactly as if it arrived from the ESP32 via TCP.
     * Triggers geofence checks, trip lifecycle, passenger tracking, and WebSocket broadcast.
     *
     * Example body:
     * {
     *   "deviceId":            "ESP32-D0776FCA4B70",
     *   "latitude":            -1.9441,
     *   "longitude":           29.8739,
     *   "speedKmh":            45.5,
     *   "headingDeg":          180.0,
     *   "passengersIn":        10,
     *   "passengersOut":       3,
     *   "passengersRemaining": 7
     * }
     */
    @PostMapping("/frame")
    public ResponseEntity<Void> pushFrame(@Valid @RequestBody SimulateRequest request) {
        vehicleDataService.save(request.toPayload());
        return ResponseEntity.ok().build();
    }

    /**
     * GET /api/v1/simulator/trips
     *
     * Lists all trips that have recorded location frames.
     * Use the returned tripId values with POST /replay to start a replay.
     */
    @GetMapping("/trips")
    public ResponseEntity<List<ReplayTripDto>> listTrips() {
        return ResponseEntity.ok(simulatorService.listTrips());
    }

    /**
     * POST /api/v1/simulator/replay
     *
     * Replays a trip's recorded GPS frames over the live WebSocket at accelerated speed.
     * - speedMultiplier 1.0  = real time
     * - speedMultiplier 5.0  = 5× speed  (default)
     * - speedMultiplier 60.0 = 60× speed (1 minute of movement per second)
     *
     * Example body:
     * { "tripId": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx", "speedMultiplier": 10.0 }
     */
    @PostMapping("/replay")
    public ResponseEntity<Map<String, Object>> startReplay(@Valid @RequestBody ReplayRequest request) {
        int frames = simulatorService.startReplay(request.tripId(), request.speedMultiplier());
        double mult = request.speedMultiplier() != null ? request.speedMultiplier() : 5.0;
        return ResponseEntity.ok(Map.of(
            "tripId", request.tripId(),
            "frames", frames,
            "speedMultiplier", mult,
            "message", "Replay started — open /tracking in the dashboard to watch"
        ));
    }

    /**
     * DELETE /api/v1/simulator/replay/{tripId}
     *
     * Stops an active replay for the given trip.
     */
    @DeleteMapping("/replay/{tripId}")
    public ResponseEntity<Void> stopReplay(@PathVariable UUID tripId) {
        simulatorService.stopReplay(tripId);
        return ResponseEntity.noContent().build();
    }
}
