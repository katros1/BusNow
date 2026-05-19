package ba.backend.plan.osrm;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Thin client for the OSRM (Open Source Routing Machine) walking route API.
 *
 * Rwanda is a country of a thousand hills — straight-line (Haversine) distances are
 * significantly shorter than actual walking paths that follow roads and terrain.
 * OSRM provides real road-network distances and accurate walking durations.
 *
 * Configured via:
 *   osrm.base-url      — default: http://router.project-osrm.org
 *   osrm.timeout-seconds — default: 5
 */
@Component
public class OsrmClient {

    private static final Logger log = LoggerFactory.getLogger(OsrmClient.class);

    private final RestClient restClient;

    public OsrmClient(
            @Value("${osrm.base-url:http://router.project-osrm.org}") String baseUrl,
            @Value("${osrm.timeout-seconds:5}") int timeoutSeconds
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
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
            // OSRM coordinate format: lng,lat;lng,lat — no URI template to avoid comma-encoding
            String path = String.format(
                    "/route/v1/foot/%.6f,%.6f;%.6f,%.6f?overview=false&steps=false",
                    fromLng, fromLat, toLng, toLat
            );
            OsrmResponse resp = restClient.get()
                    .uri(path)
                    .retrieve()
                    .body(OsrmResponse.class);

            if (resp == null || !"Ok".equals(resp.code())
                    || resp.routes() == null || resp.routes().isEmpty()) {
                return Optional.empty();
            }
            OsrmResponse.Route route = resp.routes().get(0);
            return Optional.of(new OsrmWalkResult(route.distance(), route.duration()));
        } catch (Exception e) {
            log.warn("OSRM unreachable for [{},{}] → [{},{}]: {}",
                    fromLng, fromLat, toLng, toLat, e.getMessage());
            return Optional.empty();
        }
    }

    private record OsrmResponse(String code, List<Route> routes) {
        private record Route(double distance, double duration) {
        }
    }
}
