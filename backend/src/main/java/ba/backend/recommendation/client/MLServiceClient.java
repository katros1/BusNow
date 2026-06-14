package ba.backend.recommendation.client;

import ba.backend.recommendation.dto.MLPredictRequest;
import ba.backend.recommendation.dto.RecommendationResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Component
public class MLServiceClient {

    private final WebClient webClient;
    private final boolean enabled;
    private final int timeout;

    public MLServiceClient(
            @Value("${ml-service.base-url}") String baseUrl,
            @Value("${ml-service.timeout:5000}") int timeout,
            @Value("${ml-service.enabled:true}") boolean enabled) {

        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
        this.timeout = timeout;
        this.enabled = enabled;

        log.info("ML Service Client initialized - URL: {}, Enabled: {}", baseUrl, enabled);
    }

    /**
     * Get recommendations from the ML service
     */
    public RecommendationResponse getRecommendations(MLPredictRequest request) {
        if (!enabled) {
            log.warn("ML Service is disabled, returning fallback response");
            return createFallbackResponse(request.destination());
        }

        try {
            log.info("Calling ML service for destination: {}", request.destination());

            RecommendationResponse response = webClient.post()
                    .uri("/predict")
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, clientResponse -> {
                        log.error("ML Service error: {}", clientResponse.statusCode());
                        return Mono.error(new RuntimeException("ML Service error: " + clientResponse.statusCode()));
                    })
                    .bodyToMono(RecommendationResponse.class)
                    .timeout(Duration.ofMillis(timeout))
                    .block();

            log.info("ML service response received - Best stop: {}",
                    response != null && response.bestStop() != null
                            ? response.bestStop().stopName()
                            : "none");

            return response;

        } catch (Exception e) {
            log.error("Error calling ML service: {}", e.getMessage());
            return createFallbackResponse(request.destination());
        }
    }

    /**
     * Check if ML service is healthy
     */
    public boolean isHealthy() {
        if (!enabled) {
            return false;
        }

        try {
            var response = webClient.get()
                    .uri("/health")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(2000))
                    .block();

            return response != null && response.contains("healthy");

        } catch (Exception e) {
            log.warn("ML service health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Create a fallback response when ML service is unavailable
     */
    private RecommendationResponse createFallbackResponse(String destination) {
        return RecommendationResponse.error(
                destination,
                LocalDateTime.now().toString(),
                "ML service is currently unavailable. Please try again later."
        );
    }
}