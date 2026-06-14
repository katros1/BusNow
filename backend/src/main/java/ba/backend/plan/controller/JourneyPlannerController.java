package ba.backend.plan.controller;

import ba.backend.plan.dto.JourneyNearestStopRequestDto;
import ba.backend.plan.dto.JourneyNearestStopResponseDto;
import ba.backend.plan.dto.JourneyPlanRequestDto;
import ba.backend.plan.dto.JourneyPlanResponseDto;
import ba.backend.plan.service.JourneyPlannerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/journey-planner")
public class JourneyPlannerController {

    private final JourneyPlannerService journeyPlannerService;

    public JourneyPlannerController(JourneyPlannerService journeyPlannerService) {
        this.journeyPlannerService = journeyPlannerService;
    }

    /**
     * GET /api/v1/journey-planner/plan
     *
     * Finds the best public transport routes from an origin to a destination.
     *
     * fromLat / fromLng are OPTIONAL. When omitted (GPS unavailable), the planner
     * still finds the best route to the destination but returns walkToBoardingKm=null
     * since the real walking distance to the boarding stop cannot be measured.
     *
     * Parameters — all coordinates are standard WGS-84:
     *   fromLat  – latitude  of the user's current GPS position (optional)
     *   fromLng  – longitude of the user's current GPS position (optional)
     *   toLat    – latitude  of the destination (required)
     *   toLng    – longitude of the destination (required)
     *   maxSuggestions – optional, default 5, capped at 5
     */
    @GetMapping("/plan")
    public JourneyPlanResponseDto plan(
            @RequestParam(required = false) @DecimalMin("-90.0")  @DecimalMax("90.0")  Double fromLat,
            @RequestParam(required = false) @DecimalMin("-180.0") @DecimalMax("180.0") Double fromLng,
            @RequestParam @DecimalMin("-90.0")  @DecimalMax("90.0")  double toLat,
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") double toLng,
            @RequestParam(required = false) @Positive Integer maxSuggestions
    ) {
        List<Double> origin = (fromLat != null && fromLng != null)
                ? List.of(fromLng, fromLat)
                : null;
        return journeyPlannerService.plan(new JourneyPlanRequestDto(
                origin,
                List.of(toLng, toLat),
                maxSuggestions
        ));
    }

    @PostMapping("/plan")
    public JourneyPlanResponseDto planPost(@Valid @RequestBody JourneyPlanRequestDto request) {
        return journeyPlannerService.plan(request);
    }

    @PostMapping("/nearest-stop")
    public JourneyNearestStopResponseDto nearestStop(@Valid @RequestBody JourneyNearestStopRequestDto request) {
        return journeyPlannerService.findNearestStop(request);
    }
}
