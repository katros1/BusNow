package ba.backend.routecode.controller;

import ba.backend.routecode.dto.RouteCodeCreateDto;
import ba.backend.routecode.dto.RouteCodeResponseDto;
import ba.backend.routecode.dto.RouteCodeUpdateDto;
import ba.backend.routecode.service.RouteCodeService;
import ba.backend.shared.dto.PagedResponseDto;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/route-codes")
public class RouteCodeController {

    private final RouteCodeService routeCodeService;

    public RouteCodeController(RouteCodeService routeCodeService) {
        this.routeCodeService = routeCodeService;
    }

    @PostMapping
    public ResponseEntity<RouteCodeResponseDto> create(@Valid @RequestBody RouteCodeCreateDto request) {
        RouteCodeResponseDto response = routeCodeService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public PagedResponseDto<RouteCodeResponseDto> list(
            @RequestParam(required = false) String search,
            Pageable pageable
    ) {
        return PagedResponseDto.from(routeCodeService.list(search, pageable));
    }

    @GetMapping("/{id}")
    public RouteCodeResponseDto get(@PathVariable UUID id) {
        return routeCodeService.get(id);
    }

    @PutMapping("/{id}")
    public RouteCodeResponseDto update(@PathVariable UUID id, @Valid @RequestBody RouteCodeCreateDto request) {
        return routeCodeService.update(id, request);
    }

    @PatchMapping("/{id}")
    public RouteCodeResponseDto patch(@PathVariable UUID id, @RequestBody RouteCodeUpdateDto request) {
        return routeCodeService.patch(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        routeCodeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
