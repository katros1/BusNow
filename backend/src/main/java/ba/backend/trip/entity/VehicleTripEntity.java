package ba.backend.trip.entity;

import ba.backend.bus.entity.BusEntity;
import ba.backend.route.entity.RouteEntity;
import ba.backend.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "vehicle_trips")
public class VehicleTripEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vt_bus_id", nullable = false)
    private BusEntity bus;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vt_route_id", nullable = false)
    private RouteEntity route;

    @Enumerated(EnumType.STRING)
    @Column(name = "vt_status", nullable = false)
    private TripStatus status;

    @Column(name = "vt_started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "vt_ended_at")
    private Instant endedAt;

    @Column(name = "vt_passengers_in", nullable = false)
    private int passengersIn;

    @Column(name = "vt_passengers_out", nullable = false)
    private int passengersOut;

    @Column(name = "vt_on_board", nullable = false)
    private int onBoard;

    protected VehicleTripEntity() {}

    public VehicleTripEntity(BusEntity bus, RouteEntity route, TripStatus status, Instant startedAt,
                             int passengersIn, int passengersOut, int onBoard) {
        this.bus = bus;
        this.route = route;
        this.status = status;
        this.startedAt = startedAt;
        this.passengersIn = passengersIn;
        this.passengersOut = passengersOut;
        this.onBoard = onBoard;
    }

    public BusEntity getBus()       { return bus; }
    public TripStatus getStatus()   { return status; }
    public Instant getStartedAt()   { return startedAt; }

    public void complete(Instant endedAt, int passengersIn, int passengersOut, int onBoard) {
        this.status = TripStatus.COMPLETED;
        this.endedAt = endedAt;
        this.passengersIn = passengersIn;
        this.passengersOut = passengersOut;
        this.onBoard = onBoard;
    }

    public void setOnBoard(int onBoard) {
        this.onBoard = onBoard;
    }
}
