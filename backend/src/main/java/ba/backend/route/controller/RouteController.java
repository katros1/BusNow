package ba.backend.route.controller;

import ba.backend.route.dto.RouteCreateDto;
import ba.backend.route.dto.RouteDetailResponseDto;
import ba.backend.route.dto.RouteResponseDto;
import ba.backend.route.dto.RouteStopsAssignmentDto;
import ba.backend.route.dto.RouteUpdateDto;
import ba.backend.route.service.RouteService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/routes")
public class RouteController {

    private final RouteService routeService;

    public RouteController(RouteService routeService) {
        this.routeService = routeService;
    }

    @PostMapping
    public ResponseEntity<RouteResponseDto> create(@Valid @RequestBody RouteCreateDto request) {
        RouteResponseDto response = routeService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public List<RouteResponseDto> list() {
        return routeService.list();
    }

    @GetMapping("/{id}")
    public RouteDetailResponseDto get(@PathVariable UUID id) {
        return routeService.get(id);
    }

    @PutMapping("/{id}")
    public RouteResponseDto update(@PathVariable UUID id, @Valid @RequestBody RouteCreateDto request) {
        return routeService.update(id, request);
    }

    @PatchMapping("/{id}")
    public RouteResponseDto patch(@PathVariable UUID id, @RequestBody RouteUpdateDto request) {
        return routeService.patch(id, request);
    }

    @PutMapping("/{id}/stops")
    public RouteResponseDto assignStops(@PathVariable UUID id, @Valid @RequestBody RouteStopsAssignmentDto request) {
        return routeService.assignStops(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        routeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
