package ba.backend.tracking.simulator;

import ba.backend.bus.entity.BusEntity;
import ba.backend.bus.repository.BusRepository;
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
 * Simulates a GPS tracker + beam-breaker passenger sensors mounted on a bus.
 *
 * Passenger sensor model (mirrors real hardware):
 *   • paxIn / paxOut are CUMULATIVE counters — they never reset, they only grow.
 *   • The device sends the running totals in every GPS frame.
 *   • The backend computes onBoard = (paxIn − snapshotIn) − (paxOut − snapshotOut),
 *     where snapshotIn/Out are captured at trip start.
 *   • When a new trip begins the snapshot is refreshed automatically — no explicit
 *     reset needed here.
 *
 * Passenger events:
 *   • Fired every 700–1300 m (bus-stop cadence).
 *   • Random boarding (capped by available seats) and alighting.
 *   • Everyone alights at the destination (terminal arrival).
 */
@Service
public class BusSimulatorService {

    private static final Logger log = LoggerFactory.getLogger(BusSimulatorService.class);

    /** Default seat count used when the bus entity has no capacity set. */
    private static final int DEFAULT_CAPACITY = 30;

    private final Map<String, SimulatorSession> sessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;
    private final OsrmClient          osrmClient;
    private final VehicleDataService  vehicleDataService;
    private final BusRepository       busRepository;

    public BusSimulatorService(OsrmClient osrmClient,
                               VehicleDataService vehicleDataService,
                               BusRepository busRepository) {
        this.osrmClient         = osrmClient;
        this.vehicleDataService = vehicleDataService;
        this.busRepository      = busRepository;
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

        OsrmDriveResult route    = routeOpt.get();
        List<double[]>  waypoints = route.waypoints();
        if (waypoints.size() < 2) {
            throw new IllegalStateException("OSRM returned a degenerate route (fewer than 2 waypoints).");
        }

        double[] cumDist    = cumulativeDistances(waypoints);
        double   totalDistM = cumDist[cumDist.length - 1];
        double   speedMs    = req.speedKmh() / 3.6;
        int      totalTicks = (int) Math.ceil(totalDistM / (speedMs * req.intervalS()));

        // Look up actual bus capacity from DB; fall back to default
        int capacity = busRepository.findByGpsImei(req.imei())
                .map(BusEntity::getCapacity)
                .filter(c -> c != null && c > 0)
                .orElse(DEFAULT_CAPACITY);

        SimulatorSession session = new SimulatorSession(
                req.imei(), waypoints, cumDist, totalDistM,
                req.speedKmh(), req.intervalS(), totalTicks, capacity, req.loop()
        );
        sessions.put(req.imei(), session);

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                () -> tick(session), 0, req.intervalS(), TimeUnit.SECONDS
        );
        session.future = future;

        log.info("Simulator started: imei={} route={}km waypoints={} ticks={} interval={}s capacity={}",
                req.imei(), String.format("%.2f", totalDistM / 1000.0),
                waypoints.size(), totalTicks, req.intervalS(), capacity);

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

            // ── Position advancement ──────────────────────────────────────────
            double variation        = 0.85 + s.random.nextDouble() * 0.30;  // ±15%
            double effectiveSpeedMs = (s.nominalSpeedKmh / 3.6) * variation;
            double advanceM         = effectiveSpeedMs * s.intervalS;

            double prevLat = s.currentLat;
            double prevLon = s.currentLon;

            s.distanceTraveled = Math.min(s.distanceTraveled + advanceM, s.totalDistM);

            double[] pos     = interpolate(s.waypoints, s.cumDist, s.distanceTraveled);
            double   heading = computeHeading(s, prevLat, prevLon, pos);

            double noiseLat = s.random.nextGaussian() * 0.000027;   // ≈±3 m
            double noiseLon = s.random.nextGaussian() * 0.000027;

            s.currentLat     = pos[0];
            s.currentLon     = pos[1];
            s.currentHeading = heading;
            s.uptimeSeconds += s.intervalS;
            s.sendsOk++;
            s.ticksCompleted++;

            // ── Passenger event (beam-breaker simulation) ─────────────────────
            // Fire when the bus crosses the next scheduled stop distance.
            if (s.distanceTraveled >= s.nextPassengerEventDistM
                    && s.distanceTraveled < s.totalDistM) {
                generatePassengerEvent(s);
            }

            // ── Build payload (cumulative counters like a real device) ─────────
            int satellites = 7  + s.random.nextInt(6);     // 7–12
            int rssiDbm    = -(65 + s.random.nextInt(20)); // −65..−85 dBm

            vehicleDataService.save(buildPayload(
                    s.imei,
                    pos[0] + noiseLat, pos[1] + noiseLon,
                    effectiveSpeedMs * 3.6, heading,
                    s.uptimeSeconds, s.sendsOk,
                    satellites, rssiDbm,
                    s.cumulativePaxIn, s.cumulativePaxOut
            ));

            // ── Route completion ──────────────────────────────────────────────
            if (s.distanceTraveled >= s.totalDistM) {
                // Everyone alights at the destination terminal
                int finalOnBoard = Math.max(0, s.cumulativePaxIn - s.cumulativePaxOut);
                if (finalOnBoard > 0) {
                    s.cumulativePaxOut += finalOnBoard;
                    log.debug("Terminal arrival imei={}: {} passengers alighted", s.imei, finalOnBoard);
                }
                s.tripCount++;

                if (s.loop) {
                    // Reverse direction: flip waypoints & cumulative distances, reset position.
                    // Cumulative paxIn/Out are kept — real beam-breaker hardware never resets.
                    // GpsIngestService will capture a new snapshotIn/Out when the next trip starts,
                    // so the per-trip onBoard count resets automatically on the frontend.
                    java.util.Collections.reverse(s.waypoints);
                    s.cumDist = cumulativeDistances(s.waypoints);
                    s.distanceTraveled = 0;
                    s.nextPassengerEventDistM = 300 + s.random.nextInt(500);
                    s.currentLat  = s.waypoints.get(0)[0];
                    s.currentLon  = s.waypoints.get(0)[1];
                    s.ticksCompleted = 0;
                    log.info("Simulator reversed: imei={} tripCount={} cumIn={} cumOut={}",
                            s.imei, s.tripCount, s.cumulativePaxIn, s.cumulativePaxOut);
                } else {
                    s.status = SimulatorStatus.COMPLETED;
                    s.future.cancel(false);
                    log.info("Simulator completed: imei={} trips={} distance={}km cumIn={} cumOut={}",
                            s.imei, s.tripCount,
                            String.format("%.2f", s.totalDistM / 1000.0),
                            s.cumulativePaxIn, s.cumulativePaxOut);
                    scheduler.schedule(() -> sessions.remove(s.imei), 5, TimeUnit.MINUTES);
                }
            }

        } catch (Exception e) {
            log.error("Simulator tick error for imei={}: {}", s.imei, e.getMessage(), e);
        }
    }

    // ── Passenger event ───────────────────────────────────────────────────────

    /**
     * Simulates a bus-stop event: some passengers alight, some board.
     * Respects bus capacity so the bus never carries more people than seats.
     * Updates cumulative paxIn/paxOut (never resets — just like real sensors).
     * Schedules the next event 700–1300 m ahead.
     */
    private void generatePassengerEvent(SimulatorSession s) {
        int onBoard = Math.max(0, s.cumulativePaxIn - s.cumulativePaxOut);

        // Alighting: 0–40% of on-board passengers, max 6 at once
        int maxAlight = Math.min(onBoard, 6);
        int alighting = maxAlight > 0 ? s.random.nextInt(maxAlight + 1) : 0;

        // Boarding: 1–5, capped by available seats after alighting
        int seatsAfterAlight = s.busCapacity - (onBoard - alighting);
        int maxBoard = Math.min(seatsAfterAlight, 5);
        int boarding = maxBoard > 0 ? (1 + s.random.nextInt(maxBoard)) : 0;

        s.cumulativePaxIn  += boarding;
        s.cumulativePaxOut += alighting;

        // Next stop in 700–1300 m
        s.nextPassengerEventDistM = s.distanceTraveled + 700 + s.random.nextInt(600);

        log.debug("Stop event imei={}: +{}board -{}alight | cumIn={} cumOut={} onBoard~{}",
                s.imei, boarding, alighting,
                s.cumulativePaxIn, s.cumulativePaxOut,
                s.cumulativePaxIn - s.cumulativePaxOut);
    }

    // ── Heading ───────────────────────────────────────────────────────────────

    private double computeHeading(SimulatorSession s, double prevLat, double prevLon, double[] pos) {
        if (GeoUtils.haversineM(prevLat, prevLon, pos[0], pos[1]) < 0.5) {
            for (int i = 1; i < s.waypoints.size(); i++) {
                double[] next = s.waypoints.get(i);
                if (GeoUtils.haversineM(pos[0], pos[1], next[0], next[1]) > 0.5) {
                    return bearing(pos[0], pos[1], next[0], next[1]);
                }
            }
            return s.currentHeading;
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
        double t   = (target - cumDist[lo]) / (cumDist[hi] - cumDist[lo]);
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

    /**
     * Builds a payload that mirrors a real ESP32 device frame.
     * paxIn / paxOut are the CUMULATIVE totals since the device last booted —
     * they only ever increase, exactly like beam-breaker hardware counters.
     */
    private VehiclePayload buildPayload(String imei, double lat, double lon,
            double speedKmh, double heading, long uptimeS, int sendsOk,
            int satellites, int rssiDbm,
            int cumulativePaxIn, int cumulativePaxOut) {

        VehiclePayload.GpsData gps = new VehiclePayload.GpsData();
        gps.setValid(true);
        gps.setLatitude(String.format("%.6f",  lat));
        gps.setLongitude(String.format("%.6f", lon));
        gps.setSpeedKmh(String.format("%.1f",  speedKmh));
        gps.setHeadingDeg(String.format("%.1f", heading));
        gps.setSatellites(satellites);

        VehiclePayload.Passengers pax = new VehiclePayload.Passengers();
        pax.setIn(cumulativePaxIn);
        pax.setOut(cumulativePaxOut);

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
        int estimatedOnBoard = Math.max(0, s.cumulativePaxIn - s.cumulativePaxOut);
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
                Math.round(s.totalDistM / 10.0) / 100.0,
                estimatedOnBoard,
                s.busCapacity,
                s.tripCount,
                s.cumulativePaxIn,
                s.cumulativePaxOut
        );
    }

    // ── Session state ─────────────────────────────────────────────────────────

    private static class SimulatorSession {
        final String         imei;
        // Mutable: reversed in-place when loop mode flips direction at each terminal
        final List<double[]> waypoints;
        final double         totalDistM;
        final double         nominalSpeedKmh;
        final int            intervalS;
        final int            totalTicks;
        final int            busCapacity;
        final boolean        loop;
        final Random         random = new Random();

        // Recomputed after each direction reversal
        volatile double[]           cumDist;

        volatile SimulatorStatus    status           = SimulatorStatus.RUNNING;
        volatile long               ticksCompleted   = 0;
        volatile double             distanceTraveled = 0;
        volatile double             currentLat;
        volatile double             currentLon;
        volatile double             currentHeading   = 0;
        volatile long               uptimeSeconds    = 0;
        volatile int                sendsOk          = 0;
        volatile ScheduledFuture<?> future;

        // Cumulative beam-breaker counters — never reset, only grow (across trips too)
        volatile int    cumulativePaxIn           = 0;
        volatile int    cumulativePaxOut          = 0;
        // Distance at which the next passenger-boarding event fires
        volatile double nextPassengerEventDistM;
        // How many complete legs (trips) have been run in this session
        volatile int    tripCount                 = 0;

        SimulatorSession(String imei, List<double[]> waypoints, double[] cumDist,
                double totalDistM, double nominalSpeedKmh, int intervalS,
                int totalTicks, int busCapacity, boolean loop) {
            this.imei                    = imei;
            this.waypoints               = new java.util.ArrayList<>(waypoints); // mutable copy for reversal
            this.cumDist                 = cumDist;
            this.totalDistM              = totalDistM;
            this.nominalSpeedKmh         = nominalSpeedKmh;
            this.intervalS               = intervalS;
            this.totalTicks              = totalTicks;
            this.busCapacity             = busCapacity;
            this.loop                    = loop;
            this.currentLat              = waypoints.get(0)[0];
            this.currentLon              = waypoints.get(0)[1];
            // First passenger pick-up 300–800 m after departure
            this.nextPassengerEventDistM = 300 + new Random().nextInt(500);
        }
    }

    enum SimulatorStatus { RUNNING, COMPLETED, STOPPED }
}
