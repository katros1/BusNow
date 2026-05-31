package ba.backend.tracking.simulator;

import ba.backend.plan.osrm.OsrmClient;
import ba.backend.plan.osrm.OsrmDriveResult;
import ba.backend.tracking.dto.BusSimulatorRequest;
import ba.backend.tracking.dto.SimulatorStatusResponse;
import ba.backend.tracking.service.GeoUtils;
import ba.backend.vehicledata.model.VehiclePayload;
import ba.backend.vehicledata.service.VehicleDataService;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Simulates a real GPS tracker mounted on a bus.
 *
 * Flow:
 *   1. POST /api/v1/simulator/start → fetch real driving route from OSRM
 *   2. Schedule a task at interval_s seconds
 *   3. Each tick: advance position along route at speed_kmh, compute heading,
 *      add GPS noise, vary speed ±15%, publish through the normal tracking pipeline
 *   4. Stop automatically when the bus reaches the destination
 */
@Service
public class BusSimulatorService {

    private static final Logger log = LoggerFactory.getLogger(BusSimulatorService.class);

    private final Map<String, SimulatorSession> sessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;
    private final OsrmClient osrmClient;
    private final VehicleDataService vehicleDataService;

    public BusSimulatorService(OsrmClient osrmClient, VehicleDataService vehicleDataService) {
        this.osrmClient = osrmClient;
        this.vehicleDataService = vehicleDataService;
        this.scheduler = Executors.newScheduledThreadPool(
                Math.max(4, Runtime.getRuntime().availableProcessors()),
                r -> {
                    Thread t = new Thread(r, "bus-simulator");
                    t.setDaemon(true);
                    return t;
                });
    }

    @PreDestroy
    public void shutdown() {
        sessions.values().forEach(s -> {
            if (s.future != null) s.future.cancel(false);
        });
        scheduler.shutdownNow();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public SimulatorStatusResponse start(BusSimulatorRequest req) {
        stop(req.imei());

        Optional<OsrmDriveResult> routeOpt = osrmClient.drivingRoute(
                req.originLon(), req.originLat(),
                req.destLon(),   req.destLat()
        );

        if (routeOpt.isEmpty()) {
            throw new IllegalStateException(
                    "OSRM could not find a driving route between the given coordinates. " +
                    "Verify that both points are on a navigable road network.");
        }

        OsrmDriveResult route = routeOpt.get();
        List<double[]> waypoints = route.waypoints();
        if (waypoints.size() < 2) {
            throw new IllegalStateException("OSRM returned a degenerate route (fewer than 2 waypoints).");
        }

        double[] cumDist   = cumulativeDistances(waypoints);
        double   totalDistM = cumDist[cumDist.length - 1];
        double   speedMs    = req.speedKmh() / 3.6;
        int      totalTicks = (int) Math.ceil(totalDistM / (speedMs * req.intervalS()));

        SimulatorSession session = new SimulatorSession(
                req.imei(), waypoints, cumDist, totalDistM,
                req.speedKmh(), req.intervalS(), totalTicks
        );
        sessions.put(req.imei(), session);

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                () -> tick(session), 0, req.intervalS(), TimeUnit.SECONDS
        );
        session.future = future;

        log.info("Simulator started: imei={} route={}km waypoints={} ticks={} interval={}s",
                req.imei(), String.format("%.2f", totalDistM / 1000.0), waypoints.size(), totalTicks, req.intervalS());

        return toResponse(session);
    }

    public boolean stop(String imei) {
        SimulatorSession session = sessions.remove(imei);
        if (session == null) return false;
        if (session.future != null) session.future.cancel(false);
        session.status = SimulatorStatus.STOPPED;
        log.info("Simulator stopped: imei={}", imei);
        return true;
    }

    public List<SimulatorStatusResponse> getAll() {
        return sessions.values().stream().map(this::toResponse).toList();
    }

    public Optional<SimulatorStatusResponse> getOne(String imei) {
        return Optional.ofNullable(sessions.get(imei)).map(this::toResponse);
    }

    // ── Simulation tick ───────────────────────────────────────────────────────

    private void tick(SimulatorSession s) {
        try {
            if (s.status != SimulatorStatus.RUNNING) return;

            // Speed variation ±15%: simulates acceleration, deceleration, traffic lights
            double variation      = 0.85 + s.random.nextDouble() * 0.30;
            double effectiveSpeedMs = (s.nominalSpeedKmh / 3.6) * variation;
            double advanceM       = effectiveSpeedMs * s.intervalS;

            double prevLat = s.currentLat;
            double prevLon = s.currentLon;

            s.distanceTraveled = Math.min(s.distanceTraveled + advanceM, s.totalDistM);

            double[] pos     = interpolate(s.waypoints, s.cumDist, s.distanceTraveled);
            double   heading = computeHeading(s, prevLat, prevLon, pos);

            // GPS noise: ±3 metres ≈ ±0.000027° at equator
            double noiseLat = s.random.nextGaussian() * 0.000027;
            double noiseLon = s.random.nextGaussian() * 0.000027;

            s.currentLat     = pos[0];
            s.currentLon     = pos[1];
            s.currentHeading = heading;
            s.uptimeSeconds += s.intervalS;
            s.sendsOk++;
            s.ticksCompleted++;

            int satellites = 7  + s.random.nextInt(6);        // 7–12 satellites
            int rssiDbm    = -(65 + s.random.nextInt(20));     // −65 to −85 dBm

            vehicleDataService.save(buildPayload(
                    s.imei,
                    pos[0] + noiseLat,
                    pos[1] + noiseLon,
                    effectiveSpeedMs * 3.6,
                    heading,
                    s.uptimeSeconds,
                    s.sendsOk,
                    satellites,
                    rssiDbm
            ));

            if (s.distanceTraveled >= s.totalDistM) {
                s.status = SimulatorStatus.COMPLETED;
                s.future.cancel(false);
                log.info("Simulator completed: imei={} ticks={} distance={}km",
                        s.imei, s.ticksCompleted, String.format("%.2f", s.totalDistM / 1000.0));
                // Keep the completed session briefly so callers can poll the final status
                scheduler.schedule(() -> sessions.remove(s.imei), 5, TimeUnit.MINUTES);
            }

        } catch (Exception e) {
            log.error("Simulator tick error for imei={}: {}", s.imei, e.getMessage(), e);
        }
    }

    // ── Heading ───────────────────────────────────────────────────────────────

    /**
     * Heading = bearing from previous position to current.
     * Falls back to the bearing of the upcoming route segment when the bus hasn't moved yet
     * (first tick) or has only advanced a sub-metre distance (degenerate).
     */
    private double computeHeading(SimulatorSession s, double prevLat, double prevLon, double[] pos) {
        if (GeoUtils.haversineM(prevLat, prevLon, pos[0], pos[1]) < 0.5) {
            // Look ahead: bearing toward the next distinct waypoint from current position
            for (int i = 1; i < s.waypoints.size(); i++) {
                double[] next = s.waypoints.get(i);
                if (GeoUtils.haversineM(pos[0], pos[1], next[0], next[1]) > 0.5) {
                    return bearing(pos[0], pos[1], next[0], next[1]);
                }
            }
            return s.currentHeading; // already on destination
        }
        return bearing(prevLat, prevLon, pos[0], pos[1]);
    }

    private double bearing(double lat1, double lon1, double lat2, double lon2) {
        double lat1R = Math.toRadians(lat1);
        double lat2R = Math.toRadians(lat2);
        double dLon  = Math.toRadians(lon2 - lon1);
        double y = Math.sin(dLon) * Math.cos(lat2R);
        double x = Math.cos(lat1R) * Math.sin(lat2R)
                 - Math.sin(lat1R) * Math.cos(lat2R) * Math.cos(dLon);
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
    }

    // ── Route interpolation ───────────────────────────────────────────────────

    private double[] interpolate(List<double[]> waypoints, double[] cumDist, double target) {
        if (target <= 0) return waypoints.get(0).clone();
        double total = cumDist[cumDist.length - 1];
        if (target >= total) return waypoints.get(waypoints.size() - 1).clone();

        int lo = 0, hi = cumDist.length - 1;
        while (lo + 1 < hi) {
            int mid = (lo + hi) >>> 1;
            if (cumDist[mid] <= target) lo = mid; else hi = mid;
        }
        double t  = (target - cumDist[lo]) / (cumDist[hi] - cumDist[lo]);
        double[] p1 = waypoints.get(lo);
        double[] p2 = waypoints.get(hi);
        return new double[]{ p1[0] + t * (p2[0] - p1[0]), p1[1] + t * (p2[1] - p1[1]) };
    }

    private double[] cumulativeDistances(List<double[]> wpts) {
        double[] cum = new double[wpts.size()];
        for (int i = 1; i < wpts.size(); i++) {
            double[] p1 = wpts.get(i - 1);
            double[] p2 = wpts.get(i);
            cum[i] = cum[i - 1] + GeoUtils.haversineM(p1[0], p1[1], p2[0], p2[1]);
        }
        return cum;
    }

    // ── Payload builder ───────────────────────────────────────────────────────

    private VehiclePayload buildPayload(String imei, double lat, double lon,
            double speedKmh, double heading, long uptimeS, int sendsOk,
            int satellites, int rssiDbm) {

        VehiclePayload.GpsData gps = new VehiclePayload.GpsData();
        gps.setValid(true);
        gps.setLatitude(String.format("%.6f",  lat));
        gps.setLongitude(String.format("%.6f", lon));
        gps.setSpeedKmh(String.format("%.1f",  speedKmh));
        gps.setHeadingDeg(String.format("%.1f", heading));
        gps.setSatellites(satellites);

        VehiclePayload.Passengers pax = new VehiclePayload.Passengers();
        pax.setIn(0);
        pax.setOut(0);

        VehiclePayload.DeviceData device = new VehiclePayload.DeviceData();
        device.setId(imei);
        device.setTimestamp(Instant.now().toString());
        device.setUptimeSeconds(uptimeS);
        device.setRssiDbm(rssiDbm);
        device.setSendsOk(sendsOk);

        VehiclePayload payload = new VehiclePayload();
        payload.setGps(gps);
        payload.setPassengers(pax);
        payload.setDevice(device);
        return payload;
    }

    // ── Response mapper ───────────────────────────────────────────────────────

    private SimulatorStatusResponse toResponse(SimulatorSession s) {
        double progress = s.totalDistM > 0
                ? Math.min(100.0, (s.distanceTraveled / s.totalDistM) * 100.0)
                : 0.0;
        return new SimulatorStatusResponse(
                s.imei,
                s.status.name(),
                Math.round(progress * 10.0) / 10.0,
                s.ticksCompleted,
                s.totalTicks,
                s.currentLat,
                s.currentLon,
                s.currentHeading,
                s.nominalSpeedKmh,
                s.intervalS,
                Math.round(s.totalDistM / 10.0) / 100.0   // metres → km, 2 dp
        );
    }

    // ── Session state ─────────────────────────────────────────────────────────

    private static class SimulatorSession {
        final String         imei;
        final List<double[]> waypoints;
        final double[]       cumDist;
        final double         totalDistM;
        final double         nominalSpeedKmh;
        final int            intervalS;
        final int            totalTicks;
        final Random         random = new Random();

        volatile SimulatorStatus  status           = SimulatorStatus.RUNNING;
        volatile long             ticksCompleted   = 0;
        volatile double           distanceTraveled = 0;
        volatile double           currentLat;
        volatile double           currentLon;
        volatile double           currentHeading   = 0;
        volatile long             uptimeSeconds    = 0;
        volatile int              sendsOk          = 0;
        volatile ScheduledFuture<?> future;

        SimulatorSession(String imei, List<double[]> waypoints, double[] cumDist,
                double totalDistM, double nominalSpeedKmh, int intervalS, int totalTicks) {
            this.imei            = imei;
            this.waypoints       = waypoints;
            this.cumDist         = cumDist;
            this.totalDistM      = totalDistM;
            this.nominalSpeedKmh = nominalSpeedKmh;
            this.intervalS       = intervalS;
            this.totalTicks      = totalTicks;
            this.currentLat      = waypoints.get(0)[0];
            this.currentLon      = waypoints.get(0)[1];
        }
    }

    enum SimulatorStatus { RUNNING, COMPLETED, STOPPED }
}
