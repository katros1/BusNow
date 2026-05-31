package ba.backend.tracking.controller;

import ba.backend.bus.entity.BusEntity;
import ba.backend.bus.repository.BusRepository;
import ba.backend.plan.osrm.OsrmClient;
import ba.backend.route.entity.RouteEntity;
import ba.backend.route.repository.RouteRepository;
import ba.backend.tracking.dto.BusSimulatorRequest;
import ba.backend.tracking.dto.SimulatorGpsRequest;
import ba.backend.tracking.dto.SimulatorStatusResponse;
import ba.backend.tracking.simulator.BusSimulatorService;
import ba.backend.vehicledata.model.VehiclePayload;
import ba.backend.vehicledata.service.VehicleDataService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/simulator")
public class SimulatorController {

    private final VehicleDataService  vehicleDataService;
    private final BusRepository       busRepository;
    private final RouteRepository     routeRepository;
    private final BusSimulatorService simulatorService;
    private final OsrmClient          osrmClient;

    public SimulatorController(VehicleDataService vehicleDataService,
            BusRepository busRepository, RouteRepository routeRepository,
            BusSimulatorService simulatorService, OsrmClient osrmClient) {
        this.vehicleDataService = vehicleDataService;
        this.busRepository      = busRepository;
        this.routeRepository    = routeRepository;
        this.simulatorService   = simulatorService;
        this.osrmClient         = osrmClient;
    }

    // ── Bus simulator (OSRM route + auto-advancing GPS) ───────────────────────

    /**
     * Start a bus simulation.
     *
     * Fetches a real driving route from OSRM between origin and destination,
     * then begins pushing GPS frames through the tracking pipeline at the requested
     * interval and speed — exactly as a physical GPS tracker would.
     *
     * POST /api/v1/simulator/start
     * Body: { "imei": "...", "origin_lat": -1.94, "origin_lon": 29.87,
     *         "dest_lat": -1.95, "dest_lon": 30.10, "speed_kmh": 60, "interval_s": 5 }
     */
    @PostMapping("/start")
    public ResponseEntity<?> start(@Valid @RequestBody BusSimulatorRequest req) {
        try {
            SimulatorStatusResponse status = simulatorService.start(req);
            return ResponseEntity.ok(status);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(422)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Stop a running simulation for the given IMEI.
     * DELETE /api/v1/simulator/stop/{imei}
     */
    @DeleteMapping("/stop/{imei}")
    public ResponseEntity<Void> stop(@PathVariable String imei) {
        return simulatorService.stop(imei)
                ? ResponseEntity.ok().<Void>build()
                : ResponseEntity.notFound().build();
    }

    /**
     * List all active/recently-completed simulations.
     * GET /api/v1/simulator/status
     */
    @GetMapping("/status")
    public ResponseEntity<List<SimulatorStatusResponse>> status() {
        return ResponseEntity.ok(simulatorService.getAll());
    }

    /**
     * Get status for a single simulation.
     * GET /api/v1/simulator/status/{imei}
     */
    @GetMapping("/status/{imei}")
    public ResponseEntity<SimulatorStatusResponse> statusOne(@PathVariable String imei) {
        return simulatorService.getOne(imei)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Debug: call OSRM directly and return the raw JSON response.
     * Use this to verify OSRM connectivity and that coordinates are on a road network.
     *
     * GET /api/v1/simulator/debug-route?originLat=...&originLon=...&destLat=...&destLon=...
     */
    @GetMapping(value = "/debug-route", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> debugRoute(
            @RequestParam double originLat, @RequestParam double originLon,
            @RequestParam double destLat,   @RequestParam double destLon) {
        String raw = osrmClient.rawDrivingJson(originLon, originLat, destLon, destLat);
        return ResponseEntity.ok(raw);
    }

    // ── Single-frame GPS push (existing, kept for manual testing) ────────────

    @PostMapping("/push-gps")
    public ResponseEntity<Void> pushGps(@Valid @RequestBody SimulatorGpsRequest req) {
        vehicleDataService.save(buildPayload(req.imei(), req.lat(), req.lon(),
                req.speedKmh(), req.headingDeg(), req.passengersIn(), req.passengersOut()));
        return ResponseEntity.ok().build();
    }

    /**
     * Diagnostic endpoint — shows the route/bus-park data the system will use for a given IMEI.
     * GET /api/v1/simulator/debug/{imei}
     */
    @GetMapping("/debug/{imei}")
    public ResponseEntity<Map<String, Object>> debug(@PathVariable String imei) {
        BusEntity bus = busRepository.findByGpsImei(imei).orElse(null);
        if (bus == null) {
            return ResponseEntity.ok(Map.of("error", "No bus found for IMEI: " + imei));
        }

        List<RouteEntity> routes = bus.getRouteCode() != null
                ? routeRepository.findByRouteCodeId(bus.getRouteCode().getId())
                : List.of();

        List<Map<String, Object>> routeInfo = new ArrayList<>();
        for (RouteEntity r : routes) {
            var startPark = r.getStartBusPark();
            var endPark   = r.getEndBusPark();

            Map<String, Object> startParkInfo = startPark == null ? Map.of("null", true) : Map.of(
                    "name",       startPark.getName(),
                    "hasPolygon", startPark.getPolygon() != null,
                    "polygonWkt", startPark.getPolygon() != null ? startPark.getPolygon().toString() : "null"
            );
            Map<String, Object> endParkInfo = endPark == null ? Map.of("null", true) : Map.of(
                    "name",       endPark.getName(),
                    "hasPolygon", endPark.getPolygon() != null,
                    "polygonWkt", endPark.getPolygon() != null ? endPark.getPolygon().toString() : "null"
            );

            routeInfo.add(Map.of(
                    "routeId",    r.getId(),
                    "routeName",  r.getName(),
                    "direction",  r.getDirection() != null ? r.getDirection().name() : "null",
                    "startBusPark", startParkInfo,
                    "endBusPark",   endParkInfo
            ));
        }

        return ResponseEntity.ok(Map.of(
                "imei",        imei,
                "plateNumber", bus.getPlateNumber(),
                "routeCode",   bus.getRouteCode() != null ? bus.getRouteCode().getCode() : "null",
                "routes",      routeInfo
        ));
    }

    private VehiclePayload buildPayload(String imei, double lat, double lon,
            Double speedKmh, Double headingDeg, Integer paxIn, Integer paxOut) {

        VehiclePayload.GpsData gps = new VehiclePayload.GpsData();
        gps.setValid(true);
        gps.setLatitude(String.valueOf(lat));
        gps.setLongitude(String.valueOf(lon));
        gps.setSpeedKmh(speedKmh  != null ? String.valueOf(speedKmh)  : "0.0");
        gps.setHeadingDeg(headingDeg != null ? String.valueOf(headingDeg) : "0.0");
        gps.setSatellites(8);

        VehiclePayload.Passengers passengers = new VehiclePayload.Passengers();
        passengers.setIn(paxIn  != null ? paxIn  : 0);
        passengers.setOut(paxOut != null ? paxOut : 0);

        VehiclePayload.DeviceData device = new VehiclePayload.DeviceData();
        device.setId(imei);
        device.setTimestamp(Instant.now().toString());

        VehiclePayload payload = new VehiclePayload();
        payload.setGps(gps);
        payload.setPassengers(passengers);
        payload.setDevice(device);
        return payload;
    }
}
