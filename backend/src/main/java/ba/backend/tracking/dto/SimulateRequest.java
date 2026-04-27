package ba.backend.tracking.dto;

import ba.backend.vehicledata.model.VehiclePayload;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record SimulateRequest(
        @NotBlank String deviceId,
        @NotNull  Double latitude,
        @NotNull  Double longitude,
        Double  speedKmh,
        Double  headingDeg,
        int     passengersIn,
        int     passengersOut,
        int     passengersRemaining
) {
    public VehiclePayload toPayload() {
        VehiclePayload.GpsData gps = new VehiclePayload.GpsData();
        gps.setValid(true);
        gps.setLatitude(String.valueOf(latitude));
        gps.setLongitude(String.valueOf(longitude));
        gps.setSpeedKmh(speedKmh   != null ? String.valueOf(speedKmh)   : "null");
        gps.setHeadingDeg(headingDeg != null ? String.valueOf(headingDeg) : "null");
        gps.setSatellites(8);

        VehiclePayload.Passengers pass = new VehiclePayload.Passengers();
        pass.setIn(passengersIn);
        pass.setOut(passengersOut);
        pass.setRemaining(passengersRemaining);

        VehiclePayload.DeviceData device = new VehiclePayload.DeviceData();
        device.setId(deviceId);
        device.setTimestamp(Instant.now().toString());

        VehiclePayload payload = new VehiclePayload();
        payload.setGps(gps);
        payload.setPassengers(pass);
        payload.setDevice(device);
        return payload;
    }
}
