package ba.backend.bus.controller;

import ba.backend.bus.dto.BusCreateDto;
import ba.backend.bus.dto.BusResponseDto;
import ba.backend.bus.dto.BusUpdateDto;
import ba.backend.bus.service.BusService;
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
@RequestMapping("/api/v1/buses")
public class BusController {

    private final BusService busService;

    public BusController(BusService busService) {
        this.busService = busService;
    }

    @PostMapping
    public ResponseEntity<BusResponseDto> create(@Valid @RequestBody BusCreateDto request) {
        BusResponseDto response = busService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public List<BusResponseDto> list() {
        return busService.list();
    }

    @GetMapping("/{id}")
    public BusResponseDto get(@PathVariable UUID id) {
        return busService.get(id);
    }

    @PutMapping("/{id}")
    public BusResponseDto update(@PathVariable UUID id, @Valid @RequestBody BusCreateDto request) {
        return busService.update(id, request);
    }

    @PatchMapping("/{id}")
    public BusResponseDto patch(@PathVariable UUID id, @RequestBody BusUpdateDto request) {
        return busService.patch(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        busService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
