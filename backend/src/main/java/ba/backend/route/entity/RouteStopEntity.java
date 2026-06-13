package ba.backend.route.entity;

import ba.backend.shared.entity.BaseEntity;
import ba.backend.stops.entity.StopEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "busnow_route_stop")
public class RouteStopEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "route_id", nullable = false)
    private RouteEntity route;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stop_id", nullable = false)
    private StopEntity stop;

    @Column(name = "rs_sequence", nullable = false)
    private Integer sequence;

    protected RouteStopEntity() {
    }

    public RouteStopEntity(RouteEntity route, StopEntity stop, Integer sequence) {
        this.route = route;
        this.stop = stop;
        this.sequence = sequence;
    }

    public RouteEntity getRoute() {
        return route;
    }

    public StopEntity getStop() {
        return stop;
    }

    public Integer getSequence() {
        return sequence;
    }

    public void setRoute(RouteEntity route) {
        this.route = route;
    }

    public void setStop(StopEntity stop) {
        this.stop = stop;
    }

    public void setSequence(Integer sequence) {
        this.sequence = sequence;
    }
}
