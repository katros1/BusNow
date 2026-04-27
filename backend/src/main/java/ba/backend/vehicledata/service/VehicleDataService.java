package ba.backend.vehicledata.service;

import ba.backend.tracking.event.VehicleDataReceivedEvent;
import ba.backend.vehicledata.model.VehiclePayload;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class VehicleDataService {

    private static final int MAX_HISTORY = 500;
    private final CopyOnWriteArrayList<VehiclePayload> history = new CopyOnWriteArrayList<>();
    private final ApplicationEventPublisher eventPublisher;

    public VehicleDataService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void save(VehiclePayload payload) {
        if (history.size() >= MAX_HISTORY) history.remove(0);
        history.add(payload);
        eventPublisher.publishEvent(new VehicleDataReceivedEvent(payload));
    }

    public VehiclePayload getLatest() {
        return history.isEmpty() ? null : history.get(history.size() - 1);
    }

    public List<VehiclePayload> getAll() {
        return Collections.unmodifiableList(history);
    }
}
