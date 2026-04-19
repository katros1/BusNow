package ba.backend.terminal.controller;

import ba.backend.shared.dto.PolygonDto;
import ba.backend.shared.dto.PagedResponseDto;
import ba.backend.shared.dto.PolygonResourceDto;
import ba.backend.shared.dto.PolygonUpdateDto;
import ba.backend.terminal.service.BusParkService;
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
@RequestMapping("/api/v1/terminals")
public class BusParkController {

    private final BusParkService busParkService;

    public BusParkController(BusParkService busParkService) {
        this.busParkService = busParkService;
    }

    @PostMapping
    public ResponseEntity<PolygonResourceDto> create(@Valid @RequestBody PolygonDto request) {
        PolygonResourceDto response = busParkService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public PagedResponseDto<PolygonResourceDto> list(
            @RequestParam(required = false) String search,
            Pageable pageable
    ) {
        return PagedResponseDto.from(busParkService.list(search, pageable));
    }

    @GetMapping("/{id}")
    public PolygonResourceDto get(@PathVariable UUID id) {
        return busParkService.get(id);
    }

    @PutMapping("/{id}")
    public PolygonResourceDto update(@PathVariable UUID id, @Valid @RequestBody PolygonDto request) {
        return busParkService.update(id, request);
    }

    @PatchMapping("/{id}")
    public PolygonResourceDto patch(@PathVariable UUID id, @RequestBody PolygonUpdateDto request) {
        return busParkService.patch(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        busParkService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
