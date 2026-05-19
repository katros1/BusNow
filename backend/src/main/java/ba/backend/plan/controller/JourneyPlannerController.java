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
     * Finds the best public transport routes between two coordinates.
     * Internally uses PostGIS to locate boarding/alighting stops and OSRM to
     * measure accurate walking distances (straight-line is unreliable in Rwanda's hilly terrain).
     *
     * Parameters — all coordinates are standard WGS-84:
     *   fromLat  – latitude  of the user's current position  (e.g. -1.9537)
     *   fromLng  – longitude of the user's current position  (e.g.  30.1245)
     *   toLat    – latitude  of the destination              (e.g. -1.9888)
     *   toLng    – longitude of the destination              (e.g.  30.0985)
     *   maxSuggestions – optional, default 5, capped at 5
     *
     * Example:
     *   /plan?fromLat=-1.953676525114259&fromLng=30.124516587215403
     *        &toLat=-1.988764604452511&toLng=30.098511286711155
     */
    @GetMapping("/plan")
    public JourneyPlanResponseDto plan(
            @RequestParam @DecimalMin("-90.0")  @DecimalMax("90.0")  double fromLat,
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") double fromLng,
            @RequestParam @DecimalMin("-90.0")  @DecimalMax("90.0")  double toLat,
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") double toLng,
            @RequestParam(required = false) @Positive Integer maxSuggestions
    ) {
        // Internal convention: [longitude, latitude] — matches PostGIS ST_MakePoint(lng, lat)
        return journeyPlannerService.plan(new JourneyPlanRequestDto(
                List.of(fromLng, fromLat),
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
