package ba.backend.route.entity;

import ba.backend.shared.entity.BaseEntity;
import ba.backend.terminal.entity.BusParkEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import org.locationtech.jts.geom.LineString;

@Entity
@Table(name = "iots_route")
public class RouteEntity extends BaseEntity {

    @Column(name = "rt_name", nullable = false, unique = true)
    private String name;

    @Column(name = "rt_geo", nullable = false, columnDefinition = "geometry(LineString, 4326)")
    private LineString geo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rt_start_bus_park_id", nullable = false)
    private BusParkEntity startBusPark;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rt_end_bus_park_id", nullable = false)
    private BusParkEntity endBusPark;

    @OneToMany(mappedBy = "route", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequence ASC")
    private List<RouteStopEntity> routeStops = new ArrayList<>();

    protected RouteEntity() {
    }

    public RouteEntity(String name, LineString geo, BusParkEntity startBusPark, BusParkEntity endBusPark) {
        this.name = name;
        this.geo = geo;
        this.startBusPark = startBusPark;
        this.endBusPark = endBusPark;
    }

    public String getName() {
        return name;
    }

    public LineString getGeo() {
        return geo;
    }

    public BusParkEntity getStartBusPark() {
        return startBusPark;
    }

    public BusParkEntity getEndBusPark() {
        return endBusPark;
    }

    public List<RouteStopEntity> getRouteStops() {
        return routeStops;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setGeo(LineString geo) {
        this.geo = geo;
    }

    public void setStartBusPark(BusParkEntity startBusPark) {
        this.startBusPark = startBusPark;
    }

    public void setEndBusPark(BusParkEntity endBusPark) {
        this.endBusPark = endBusPark;
    }
}
