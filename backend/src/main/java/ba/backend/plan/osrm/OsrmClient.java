package ba.backend.plan.osrm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Thin client for the OSRM (Open Source Routing Machine) routing API.
 *
 * Rwanda is a country of a thousand hills — straight-line (Haversine) distances are
 * significantly shorter than actual paths that follow roads and terrain.
 * OSRM provides real road-network distances and accurate durations.
 *
 * Configured via:
 *   osrm.base-url        — default: http://router.project-osrm.org
 *   osrm.timeout-seconds — default: 10
 */
@Component
public class OsrmClient {

    private static final Logger log = LoggerFactory.getLogger(OsrmClient.class);

    private final RestClient   restClient;
    private final ObjectMapper mapper;

    public OsrmClient(
            @Value("${osrm.base-url:http://router.project-osrm.org}") String baseUrl,
            @Value("${osrm.timeout-seconds:10}") int timeoutSeconds,
            ObjectMapper mapper
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("Accept-Encoding", "identity")  // prevent gzip — JdkClientHttpRequestFactory doesn't decompress
                .build();
        this.mapper = mapper;
    }

    /**
     * Fetches the walking route distance and duration between two coordinates from OSRM.
     * Returns {@link Optional#empty()} on any network error or when OSRM returns no route,
     * allowing the caller to decide on a fallback strategy.
     *
     * @param fromLng departure longitude
     * @param fromLat departure latitude
     * @param toLng   destination longitude
     * @param toLat   destination latitude
     */
    public Optional<OsrmWalkResult> walkingRoute(
            double fromLng, double fromLat,
            double toLng,   double toLat
    ) {
        try {
            String path = String.format(
                    "/route/v1/foot/%.6f,%.6f;%.6f,%.6f?overview=false&steps=false",
                    fromLng, fromLat, toLng, toLat
            );
            String body = restClient.get().uri(path).retrieve().body(String.class);
            if (body == null) return Optional.empty();

            JsonNode root = mapper.readTree(body);
            if (!"Ok".equals(root.path("code").asText())) return Optional.empty();

            JsonNode routes = root.path("routes");
            if (!routes.isArray() || routes.isEmpty()) return Optional.empty();

            JsonNode route = routes.get(0);
            return Optional.of(new OsrmWalkResult(
                    route.path("distance").asDouble(),
                    route.path("duration").asDouble()
            ));
        } catch (Exception e) {
            log.warn("OSRM walking route failed [{},{}]→[{},{}]: {}",
                    fromLng, fromLat, toLng, toLat, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Fetches a real driving route between two coordinates from OSRM, including the full
     * geometry (all road-snapped waypoints). Used by the bus simulator to follow an authentic
     * road-network path rather than a straight line.
     *
     * Parses the raw JSON string with JsonNode to avoid Jackson generic-type
     * deserialization issues with nested private records.
     *
     * @param fromLng departure longitude
     * @param fromLat departure latitude
     * @param toLng   destination longitude
     * @param toLat   destination latitude
     */
    public Optional<OsrmDriveResult> drivingRoute(
            double fromLng, double fromLat,
            double toLng,   double toLat
    ) {
        String path = String.format(
                "/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson",
                fromLng, fromLat, toLng, toLat
        );
        try {
            String body = restClient.get().uri(path).retrieve().body(String.class);
            if (body == null || body.isBlank()) {
                log.warn("OSRM driving route: empty response for path {}", path);
                return Optional.empty();
            }

            JsonNode root = mapper.readTree(body);
            String code = root.path("code").asText();
            if (!"Ok".equals(code)) {
                log.warn("OSRM driving route: code={} message={} path={}",
                        code, root.path("message").asText("(none)"), path);
                return Optional.empty();
            }

            JsonNode routes = root.path("routes");
            if (!routes.isArray() || routes.isEmpty()) {
                log.warn("OSRM driving route: no routes in response for path {}", path);
                return Optional.empty();
            }

            JsonNode route    = routes.get(0);
            double   distance = route.path("distance").asDouble();
            double   duration = route.path("duration").asDouble();

            JsonNode coords = route.path("geometry").path("coordinates");
            if (!coords.isArray() || coords.isEmpty()) {
                log.warn("OSRM driving route: geometry missing or empty for path {}", path);
                return Optional.empty();
            }

            // OSRM geometry coordinates are [lng, lat] — convert to [lat, lon]
            List<double[]> waypoints = new ArrayList<>(coords.size());
            for (JsonNode c : coords) {
                waypoints.add(new double[]{ c.get(1).asDouble(), c.get(0).asDouble() });
            }

            log.debug("OSRM driving route: {}m {}s {} waypoints", distance, duration, waypoints.size());
            return Optional.of(new OsrmDriveResult(distance, duration, waypoints));

        } catch (Exception e) {
            log.warn("OSRM driving route exception for path {}: {}", path, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Returns the raw OSRM driving JSON for a given pair of coordinates.
     * Used by the simulator debug endpoint to inspect the OSRM response directly.
     */
    public String rawDrivingJson(double fromLng, double fromLat, double toLng, double toLat) {
        try {
            String path = String.format(
                    "/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson",
                    fromLng, fromLat, toLng, toLat
            );
            return restClient.get().uri(path).retrieve().body(String.class);
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }
}
