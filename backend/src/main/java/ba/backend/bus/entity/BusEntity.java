package ba.backend.bus.entity;

import ba.backend.driver.entity.DriverEntity;
import ba.backend.routecode.entity.RouteCodeEntity;
import ba.backend.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "iots_bus")
public class BusEntity extends BaseEntity {

    @Column(name = "bus_plate_number", nullable = false, unique = true)
    private String plateNumber;

    @Column(name = "bus_gps_imei", nullable = false, unique = true)
    private String gpsImei;

    @Column(name = "bus_model")
    private String model;

    @Column(name = "bus_capacity")
    private Integer capacity;

    @Column(name = "bus_current_latitude")
    private Double currentLatitude;

    @Column(name = "bus_current_longitude")
    private Double currentLongitude;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bus_current_driver_id")
    private DriverEntity currentDriver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bus_route_code_id")
    private RouteCodeEntity routeCode;

    protected BusEntity() {
    }

    public BusEntity(
            String plateNumber,
            String gpsImei,
            String model,
            Integer capacity,
            Double currentLatitude,
            Double currentLongitude,
            DriverEntity currentDriver,
            RouteCodeEntity routeCode
    ) {
        this.plateNumber = plateNumber;
        this.gpsImei = gpsImei;
        this.model = model;
        this.capacity = capacity;
        this.currentLatitude = currentLatitude;
        this.currentLongitude = currentLongitude;
        this.currentDriver = currentDriver;
        this.routeCode = routeCode;
    }

    public String getPlateNumber() {
        return plateNumber;
    }

    public String getGpsImei() {
        return gpsImei;
    }

    public String getModel() {
        return model;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public Double getCurrentLatitude() {
        return currentLatitude;
    }

    public Double getCurrentLongitude() {
        return currentLongitude;
    }

    public DriverEntity getCurrentDriver() {
        return currentDriver;
    }

    public RouteCodeEntity getRouteCode() {
        return routeCode;
    }

    public void setPlateNumber(String plateNumber) {
        this.plateNumber = plateNumber;
    }

    public void setGpsImei(String gpsImei) {
        this.gpsImei = gpsImei;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public void setCurrentLatitude(Double currentLatitude) {
        this.currentLatitude = currentLatitude;
    }

    public void setCurrentLongitude(Double currentLongitude) {
        this.currentLongitude = currentLongitude;
    }

    public void setCurrentDriver(DriverEntity currentDriver) {
        this.currentDriver = currentDriver;
    }

    public void setRouteCode(RouteCodeEntity routeCode) {
        this.routeCode = routeCode;
    }
}
