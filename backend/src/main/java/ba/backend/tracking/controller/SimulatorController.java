package ba.backend.tracking.controller;

import ba.backend.bus.entity.BusEntity;
import ba.backend.bus.repository.BusRepository;
import ba.backend.route.entity.RouteEntity;
import ba.backend.route.repository.RouteRepository;
import ba.backend.tracking.dto.SimulatorGpsRequest;
import ba.backend.tracking.dto.SimulatorRouteWalkRequest;
import ba.backend.tracking.dto.VehicleLiveSnapshot;
import ba.backend.tracking.service.GpsIngestService;
import ba.backend.vehicledata.model.VehiclePayload;
import ba.backend.vehicledata.service.VehicleDataService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.locationtech.jts.geom.Coordinate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GPS simulator for end-to-end tracking pipeline testing.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST /api/v1/simulator/push-gps        – push a single GPS fix</li>
 *   <li>POST /api/v1/simulator/walk-route       – walk a bus through all stops → tests toggle</li>
 *   <li>GET  /api/v1/simulator/bus-state/{imei} – current live snapshot for a bus</li>
 *   <li>GET  /api/v1/simulator/buses            – all buses with IMEI and last snapshot</li>
 * </ul>
 *
 * No auth required (SecurityConfig permits /api/v1/simulator/**).
 */
@RestController
@RequestMapping("/api/v1/simulator")
public class SimulatorController {

    private final VehicleDataService vehicleDataService;
    private final GpsIngestService   gpsIngestService;
    private final BusRepository      busRepository;
    private final RouteRepository    routeRepository;

    public SimulatorController(VehicleDataService vehicleDataService,
            GpsIngestService gpsIngestService,
            BusRepository busRepository,
            RouteRepository routeRepository) {
        this.vehicleDataService = vehicleDataService;
        this.gpsIngestService   = gpsIngestService;
        this.busRepository      = busRepository;
        this.routeRepository    = routeRepository;
    }

    // ── Single GPS push ───────────────────────────────────────────────────────

    @PostMapping("/push-gps")
    public ResponseEntity<Map<String, Object>> pushGps(@Valid @RequestBody SimulatorGpsRequest req) {
        vehicleDataService.save(buildPayload(req.imei(), req.lat(), req.lon(),
                req.speedKmh(), req.headingDeg(), req.passengersIn(), req.passengersOut()));

        VehicleLiveSnapshot snapshot = snapshotForImei(req.imei());
        return ResponseEntity.ok(Map.of(
                "ok", true,
                "imei", req.imei(),
                "snapshot", snapshot != null ? snapshot : "Bus not found or no state yet"
        ));
    }

    // ── Route walk simulation ─────────────────────────────────────────────────

    /**
     * Walks the bus through every stop on the given route, then parks it at the
     * end bus park (triggering trip completion + direction toggle for next trip).
     *
     * Response lists each simulation step with stop name and resulting snapshot.
     */
    @PostMapping("/walk-route")
    public ResponseEntity<Map<String, Object>> walkRoute(
            @Valid @RequestBody SimulatorRouteWalkRequest req) {

        RouteEntity route = routeRepository.findById(req.routeId()).orElse(null);
        if (route == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Route not found: " + req.routeId()));
        }

        BusEntity bus = busRepository.findAll().stream()
                .filter(b -> b.getGpsImei().equals(req.imei()))
                .findFirst().orElse(null);
        if (bus == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Bus not found for IMEI: " + req.imei()));
        }

        double speed     = req.speedKmh()     != null ? req.speedKmh()     : 40.0;
        int    paxIn     = req.passengersIn()  != null ? req.passengersIn() : 0;
        int    paxOut    = req.passengersOut() != null ? req.passengersOut(): 0;

        List<Map<String, Object>> steps = new ArrayList<>();

        // Step 1: depart from start bus park (put bus just at the start park centroid first)
        if (route.getStartBusPark() != null && route.getStartBusPark().getPolygon() != null) {
            Point parkCentroid = route.getStartBusPark().getPolygon().getCentroid();
            pushAndRecord(steps, req.imei(), "START_PARK:" + route.getStartBusPark().getName(),
                    parkCentroid.getY(), parkCentroid.getX(), speed, 0.0, paxIn, paxOut);

            // One tick outside the start park — triggers trip start
            double[] outside = pointOutside(parkCentroid.getY(), parkCentroid.getX());
            pushAndRecord(steps, req.imei(), "DEPART_START_PARK",
                    outside[0], outside[1], speed, 0.0, paxIn, paxOut);
        }

        // Step 2: walk through each stop in sequence
        List<RouteStopEntity> stops = route.getRouteStops().stream()
                .sorted(Comparator.comparingInt(RouteStopEntity::getSequence))
                .toList();

        for (RouteStopEntity rs : stops) {
            if (rs.getStop() == null || rs.getStop().getGeo() == null) continue;
            Point centroid = rs.getStop().getGeo().getCentroid();
            pushAndRecord(steps, req.imei(),
                    "STOP[" + rs.getSequence() + "]:" + rs.getStop().getName(),
                    centroid.getY(), centroid.getX(), speed, 0.0,
                    paxIn + rs.getSequence() * 3,      // simulate boarding
                    paxOut + rs.getSequence() * 2);    // simulate alighting
        }

        // Step 3: arrive at end bus park — triggers trip completion
        if (route.getEndBusPark() != null && route.getEndBusPark().getPolygon() != null) {
            Point endCentroid = route.getEndBusPark().getPolygon().getCentroid();
            pushAndRecord(steps, req.imei(), "END_PARK:" + route.getEndBusPark().getName(),
                    endCentroid.getY(), endCentroid.getX(), 0.0, 0.0, paxIn, paxOut);

            // Step 4: depart end park — triggers next trip start (direction toggle)
            double[] outside = pointOutside(endCentroid.getY(), endCentroid.getX());
            pushAndRecord(steps, req.imei(), "DEPART_END_PARK_→_TOGGLE",
                    outside[0], outside[1], speed, 180.0, paxIn, paxOut);
        }

        VehicleLiveSnapshot finalSnap = snapshotForImei(req.imei());
        return ResponseEntity.ok(Map.of(
                "route",     route.getName(),
                "direction", route.getDirection() != null ? route.getDirection().name() : "UNKNOWN",
                "steps",     steps,
                "finalSnapshot", finalSnap != null ? finalSnap : "none"
        ));
    }

    // ── Bus state queries ─────────────────────────────────────────────────────

    @GetMapping("/bus-state/{imei}")
    public ResponseEntity<Map<String, Object>> getBusState(@PathVariable String imei) {
        VehicleLiveSnapshot snapshot = snapshotForImei(imei);
        if (snapshot == null) {
            return ResponseEntity.ok(Map.of("imei", imei, "state", "no data yet"));
        }
        return ResponseEntity.ok(Map.of("imei", imei, "snapshot", snapshot));
    }

    @GetMapping("/buses")
    public ResponseEntity<List<Map<String, Object>>> listBuses() {
        List<Map<String, Object>> result = busRepository.findAll().stream()
                .map(bus -> {
                    VehicleLiveSnapshot snap = snapshotForImei(bus.getGpsImei());
                    return Map.<String, Object>of(
                            "imei",        bus.getGpsImei(),
                            "plateNumber", bus.getPlateNumber(),
                            "model",       bus.getModel() != null ? bus.getModel() : "",
                            "capacity",    bus.getCapacity() != null ? bus.getCapacity() : 0,
                            "routeCode",   bus.getRouteCode() != null ? bus.getRouteCode().getCode() : "",
                            "snapshot",    snap != null ? snap : "no live data"
                    );
                })
                .toList();
        return ResponseEntity.ok(result);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void pushAndRecord(List<Map<String, Object>> steps, String imei, String label,
            double lat, double lon, double speedKmh, double headingDeg,
            int paxIn, int paxOut) {

        vehicleDataService.save(buildPayload(imei, lat, lon, speedKmh, headingDeg, paxIn, paxOut));

        VehicleLiveSnapshot snap = snapshotForImei(imei);
        steps.add(Map.of(
                "step",     label,
                "lat",      lat,
                "lon",      lon,
                "snapshot", snap != null ? snap : "pending"
        ));
    }

    private VehicleLiveSnapshot snapshotForImei(String imei) {
        return gpsIngestService.getBusStates().values().stream()
                .map(GpsIngestService.BusState::lastSnapshot)
                .filter(s -> s != null && imei.equals(s.plateNumber()))
                .findFirst()
                // also try matching by IMEI via bus lookup
                .orElseGet(() -> {
                    BusEntity bus = busRepository.findByGpsImei(imei).orElse(null);
                    if (bus == null) return null;
                    GpsIngestService.BusState state = gpsIngestService.getBusStates().get(bus.getId());
                    return state != null ? state.lastSnapshot() : null;
                });
    }

    private VehiclePayload buildPayload(String imei, double lat, double lon,
            Double speedKmh, Double headingDeg, Integer paxIn, Integer paxOut) {

        VehiclePayload.GpsData gps = new VehiclePayload.GpsData();
        gps.setValid(true);
        gps.setLatitude(String.valueOf(lat));
        gps.setLongitude(String.valueOf(lon));
        gps.setSpeedKmh(speedKmh != null ? String.valueOf(speedKmh) : "0.0");
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

    /** Returns a coordinate ~100 m north-east of the given point (always outside a bus park). */
    private double[] pointOutside(double lat, double lon) {
        return new double[]{lat + 0.001, lon + 0.001};
    }
}
