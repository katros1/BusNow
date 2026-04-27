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
@Table(name = "iots_trip")
public class TripEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tr_bus_id", nullable = false)
    private BusEntity bus;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tr_route_id", nullable = false)
    private RouteEntity route;

    @Enumerated(EnumType.STRING)
    @Column(name = "tr_status", nullable = false)
    private TripStatus status;

    @Column(name = "tr_started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "tr_ended_at")
    private Instant endedAt;

    // Device-level counters at trip start — used to compute per-trip deltas
    @Column(name = "tr_snapshot_in", nullable = false)
    private int snapshotIn;

    @Column(name = "tr_snapshot_out", nullable = false)
    private int snapshotOut;

    @Column(name = "tr_passengers_on_board", nullable = false)
    private int passengersOnBoard;

    // Device counters captured when trip ends — diff against snapshot gives trip totals
    @Column(name = "tr_final_in")
    private Integer finalIn;

    @Column(name = "tr_final_out")
    private Integer finalOut;

    protected TripEntity() {}

    public TripEntity(BusEntity bus, RouteEntity route, Instant startedAt,
                      int snapshotIn, int snapshotOut, int passengersOnBoard) {
        this.bus = bus;
        this.route = route;
        this.status = TripStatus.ACTIVE;
        this.startedAt = startedAt;
        this.snapshotIn = snapshotIn;
        this.snapshotOut = snapshotOut;
        this.passengersOnBoard = passengersOnBoard;
    }

    public BusEntity getBus() { return bus; }
    public RouteEntity getRoute() { return route; }
    public TripStatus getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public int getSnapshotIn() { return snapshotIn; }
    public int getSnapshotOut() { return snapshotOut; }
    public int getPassengersOnBoard() { return passengersOnBoard; }
    public Integer getFinalIn()       { return finalIn; }
    public Integer getFinalOut()      { return finalOut; }

    public void complete(Instant endedAt, int finalIn, int finalOut) {
        this.status   = TripStatus.COMPLETED;
        this.endedAt  = endedAt;
        this.finalIn  = finalIn;
        this.finalOut = finalOut;
    }

    public void updatePassengersOnBoard(int count) {
        this.passengersOnBoard = count;
    }
}
