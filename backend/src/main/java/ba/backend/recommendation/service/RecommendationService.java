package ba.backend.recommendation.service;

import ba.backend.recommendation.client.MLServiceClient;
import ba.backend.recommendation.dto.MLPredictRequest;
import ba.backend.recommendation.dto.RecommendationRequest;
import ba.backend.recommendation.dto.RecommendationResponse;
import ba.backend.recommendation.dto.StopRecommendation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final MLServiceClient mlServiceClient;

    // List of valid destinations
    private static final List<String> VALID_DESTINATIONS = List.of(
            "Downtown", "Kimicanga", "La Colombiere", "RIB", "MINIJUST",
            "Gishushu", "Kigali Heights", "Beausejour", "Kisimenti",
            "Stadium", "Remera", "Sonatubes", "Chez Lando",
            "Kwa Rwahama", "Kicukiro Centre", "Kimironko"
    );

    /**
     * Get bus stop recommendations based on user request
     */
    public RecommendationResponse getRecommendations(RecommendationRequest request) {
        log.info("Processing recommendation request for destination: {}", request.destination());

        // Validate destination
        if (!isValidDestination(request.destination())) {
            return RecommendationResponse.error(
                    request.destination(),
                    LocalDateTime.now().toString(),
                    "Invalid destination. Available destinations: " + VALID_DESTINATIONS
            );
        }

        // Set current time if not provided
        LocalDateTime now = LocalDateTime.now();
        int hour = request.hour() != null ? request.hour() : now.getHour();
        int dayOfWeek = request.dayOfWeek() != null ? request.dayOfWeek() : now.getDayOfWeek().getValue();

        // Build ML request using factory method
        MLPredictRequest mlRequest = MLPredictRequest.from(request, hour, dayOfWeek);

        // Call ML service
        RecommendationResponse response = mlServiceClient.getRecommendations(mlRequest);

        // Enrich response with additional data if needed
        if (response.success() != null && response.success()) {
            enrichRecommendations(response);
        }

        return response;
    }

    /**
     * Get list of valid destinations
     */
    public List<String> getDestinations() {
        return VALID_DESTINATIONS;
    }

    /**
     * Check ML service health
     */
    public boolean isMLServiceHealthy() {
        return mlServiceClient.isHealthy();
    }

    /**
     * Check if destination is valid
     */
    private boolean isValidDestination(String destination) {
        return VALID_DESTINATIONS.stream()
                .anyMatch(d -> d.equalsIgnoreCase(destination));
    }

    /**
     * Enrich recommendations with additional database data
     * You can add more data from your existing Stop/Route entities here
     */
    private void enrichRecommendations(RecommendationResponse response) {
        if (response.recommendations() == null) {
            return;
        }

        for (StopRecommendation rec : response.recommendations()) {
            log.debug("Recommendation: {} - Confidence: {}%",
                    rec.stopName(), rec.confidence());
        }
    }
}