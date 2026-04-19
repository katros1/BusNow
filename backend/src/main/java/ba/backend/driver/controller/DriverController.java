package ba.backend.driver.controller;

import ba.backend.driver.dto.DriverCreateDto;
import ba.backend.driver.dto.DriverResponseDto;
import ba.backend.driver.dto.DriverUpdateDto;
import ba.backend.driver.entity.DriverGender;
import ba.backend.driver.entity.LicenseCategory;
import ba.backend.driver.service.DriverService;
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
@RequestMapping("/api/v1/drivers")
public class DriverController {

    private final DriverService driverService;

    public DriverController(DriverService driverService) {
        this.driverService = driverService;
    }

    @PostMapping
    public ResponseEntity<DriverResponseDto> create(@Valid @RequestBody DriverCreateDto request) {
        DriverResponseDto response = driverService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public PagedResponseDto<DriverResponseDto> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) DriverGender gender,
            @RequestParam(required = false) LicenseCategory licenseCategory,
            Pageable pageable
    ) {
        return PagedResponseDto.from(driverService.list(search, gender, licenseCategory, pageable));
    }

    @GetMapping("/{id}")
    public DriverResponseDto get(@PathVariable UUID id) {
        return driverService.get(id);
    }

    @PutMapping("/{id}")
    public DriverResponseDto update(@PathVariable UUID id, @Valid @RequestBody DriverCreateDto request) {
        return driverService.update(id, request);
    }

    @PatchMapping("/{id}")
    public DriverResponseDto patch(@PathVariable UUID id, @RequestBody DriverUpdateDto request) {
        return driverService.patch(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        driverService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
