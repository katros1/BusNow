package ba.backend.recommendation.controller;

import ba.backend.recommendation.dto.RecommendationRequest;
import ba.backend.recommendation.dto.RecommendationResponse;
import ba.backend.recommendation.service.RecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/recommendations")
@RequiredArgsConstructor
@Tag(name = "Recommendations", description = "AI-powered bus stop recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;

    /**
     * Get AI-powered bus stop recommendations
     */
    @PostMapping
    @Operation(summary = "Get bus stop recommendations",
            description = "Returns AI-powered recommendations for the best bus stop based on destination and current location")
    public ResponseEntity<RecommendationResponse> getRecommendations(
            @Valid @RequestBody RecommendationRequest request) {

        log.info("Recommendation request received - Destination: {}, Location: ({}, {})",
                request.destination(),
                request.userLatitude(),
                request.userLongitude());

        RecommendationResponse response = recommendationService.getRecommendations(request);

        // Always return 200 — success/failure is expressed in the response body.
        // HTTP 400 is reserved for structural validation errors (@Valid on the request).
        return ResponseEntity.ok(response);
    }

    /**
     * Get recommendations via GET (alternative endpoint for simple requests)
     */
    @GetMapping
    @Operation(summary = "Get recommendations (GET method)",
            description = "Alternative endpoint using query parameters")
    public ResponseEntity<RecommendationResponse> getRecommendationsGet(
            @RequestParam String destination,
            @RequestParam Double lat,
            @RequestParam Double lng,
            @RequestParam(required = false) Integer hour,
            @RequestParam(required = false) Integer dayOfWeek) {

        RecommendationRequest request = new RecommendationRequest(
                destination, lat, lng, hour, dayOfWeek
        );

        return getRecommendations(request);
    }

    /**
     * Get list of available destinations
     */
    @GetMapping("/destinations")
    @Operation(summary = "Get available destinations")
    public ResponseEntity<Map<String, Object>> getDestinations() {
        List<String> destinations = recommendationService.getDestinations();

        return ResponseEntity.ok(Map.of(
                "success", true,
                "destinations", destinations,
                "count", destinations.size()
        ));
    }

    /**
     * Check ML service health
     */
    @GetMapping("/health")
    @Operation(summary = "Check AI service health")
    public ResponseEntity<Map<String, Object>> checkHealth() {
        boolean healthy = recommendationService.isMLServiceHealthy();

        return ResponseEntity.ok(Map.of(
                "mlServiceHealthy", healthy,
                "status", healthy ? "operational" : "degraded"
        ));
    }
}