package ba.backend.vehicledata.service;

import ba.backend.vehicledata.model.VehiclePayload;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class VehicleDataService {

    private static final int MAX_HISTORY = 500;
    private final CopyOnWriteArrayList<VehiclePayload> history = new CopyOnWriteArrayList<>();

    public void save(VehiclePayload payload) {
        if (history.size() >= MAX_HISTORY) history.remove(0);
        history.add(payload);
    }

    public VehiclePayload getLatest() {
        return history.isEmpty() ? null : history.get(history.size() - 1);
    }

    public List<VehiclePayload> getAll() {
        return Collections.unmodifiableList(history);
    }
}
