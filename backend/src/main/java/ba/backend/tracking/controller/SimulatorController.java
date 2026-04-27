package ba.backend.tracking.controller;

import ba.backend.tracking.dto.SimulateRequest;
import ba.backend.vehicledata.service.VehicleDataService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/simulator")
public class SimulatorController {

    private final VehicleDataService vehicleDataService;

    public SimulatorController(VehicleDataService vehicleDataService) {
        this.vehicleDataService = vehicleDataService;
    }

    /**
     * POST /api/v1/simulator/frame
     *
     * Injects a GPS frame exactly as if it arrived from the ESP32 via TCP.
     * Triggers geofence checks, trip lifecycle, passenger tracking, and WebSocket broadcast.
     *
     * Example body:
     * {
     *   "deviceId":             "ESP32-D0776FCA4B70",
     *   "latitude":             43.8563,
     *   "longitude":            18.4131,
     *   "speedKmh":             45.5,
     *   "headingDeg":           180.0,
     *   "passengersIn":         10,
     *   "passengersOut":        3,
     *   "passengersRemaining":  7
     * }
     */
    @PostMapping("/frame")
    public ResponseEntity<Void> pushFrame(@Valid @RequestBody SimulateRequest request) {
        vehicleDataService.save(request.toPayload());
        return ResponseEntity.ok().build();
    }
}
