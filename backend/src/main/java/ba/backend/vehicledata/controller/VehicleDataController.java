package ba.backend.vehicledata.controller;

import ba.backend.vehicledata.model.VehiclePayload;
import ba.backend.vehicledata.service.VehicleDataService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/vehicle-data")
@CrossOrigin(origins = "*")
public class VehicleDataController {

    private final VehicleDataService service;

    public VehicleDataController(VehicleDataService service) {
        this.service = service;
    }

    @GetMapping("/latest")
    public ResponseEntity<VehiclePayload> latest() {
        VehiclePayload data = service.getLatest();
        return data != null ? ResponseEntity.ok(data) : ResponseEntity.noContent().build();
    }

    @GetMapping("/history")
    public ResponseEntity<List<VehiclePayload>> history() {
        return ResponseEntity.ok(service.getAll());
    }
}
