package ba.backend.plan.controller;

import ba.backend.plan.dto.JourneyNearestStopRequestDto;
import ba.backend.plan.dto.JourneyNearestStopResponseDto;
import ba.backend.plan.dto.JourneyPlanRequestDto;
import ba.backend.plan.dto.JourneyPlanResponseDto;
import ba.backend.plan.service.JourneyPlannerService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/journey-planner")
public class JourneyPlannerController {

    private final JourneyPlannerService journeyPlannerService;

    public JourneyPlannerController(JourneyPlannerService journeyPlannerService) {
        this.journeyPlannerService = journeyPlannerService;
    }

    @PostMapping("/plan")
    public JourneyPlanResponseDto plan(@Valid @RequestBody JourneyPlanRequestDto request) {
        return journeyPlannerService.plan(request);
    }

    @PostMapping("/nearest-stop")
    public JourneyNearestStopResponseDto nearestStop(@Valid @RequestBody JourneyNearestStopRequestDto request) {
        return journeyPlannerService.findNearestStop(request);
    }
}
