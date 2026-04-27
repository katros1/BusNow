package ba.backend.tracking.event;

import ba.backend.vehicledata.model.VehiclePayload;

public record VehicleDataReceivedEvent(VehiclePayload payload) {}
