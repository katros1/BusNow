package ba.backend.tracking.entity;

import ba.backend.bus.entity.BusEntity;
import ba.backend.shared.entity.BaseEntity;
import ba.backend.stops.entity.StopEntity;
import ba.backend.trip.entity.TripEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "busnow_vehicle_location", indexes = {
        @Index(name = "idx_vl_bus_id",      columnList = "vl_bus_id"),
        @Index(name = "idx_vl_trip_id",     columnList = "vl_trip_id"),
        @Index(name = "idx_vl_stop_id",     columnList = "vl_stop_id"),
        @Index(name = "idx_vl_recorded_at", columnList = "vl_recorded_at")
})
public class VehicleLocationEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vl_bus_id", nullable = false)
    private BusEntity bus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vl_trip_id")
    private TripEntity trip;

    /** Which bus stop the vehicle was in when this frame was recorded. Null when between stops. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vl_stop_id")
    private StopEntity currentStop;

    @Column(name = "vl_latitude",  nullable = false)
    private double latitude;

    @Column(name = "vl_longitude", nullable = false)
    private double longitude;

    @Column(name = "vl_speed_kmh")
    private Double speedKmh;

    @Column(name = "vl_heading_deg")
    private Double headingDeg;

    @Column(name = "vl_passengers_on_board")
    private Integer passengersOnBoard;

    @Column(name = "vl_recorded_at", nullable = false)
    private Instant recordedAt;

    protected VehicleLocationEntity() {}

    public VehicleLocationEntity(BusEntity bus, TripEntity trip, StopEntity currentStop,
                                 double latitude, double longitude,
                                 Double speedKmh, Double headingDeg,
                                 Integer passengersOnBoard, Instant recordedAt) {
        this.bus = bus;
        this.trip = trip;
        this.currentStop = currentStop;
        this.latitude = latitude;
        this.longitude = longitude;
        this.speedKmh = speedKmh;
        this.headingDeg = headingDeg;
        this.passengersOnBoard = passengersOnBoard;
        this.recordedAt = recordedAt;
    }

    public BusEntity getBus()             { return bus; }
    public TripEntity getTrip()           { return trip; }
    public StopEntity getCurrentStop()    { return currentStop; }
    public double getLatitude()           { return latitude; }
    public double getLongitude()          { return longitude; }
    public Double getSpeedKmh()           { return speedKmh; }
    public Double getHeadingDeg()         { return headingDeg; }
    public Integer getPassengersOnBoard() { return passengersOnBoard; }
    public Instant getRecordedAt()        { return recordedAt; }
}
