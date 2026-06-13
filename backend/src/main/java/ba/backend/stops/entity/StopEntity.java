package ba.backend.stops.entity;

import ba.backend.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.locationtech.jts.geom.Polygon;

@Entity
@Table(name = "busnow_bus_stop")
public class StopEntity extends BaseEntity {
    @Column(name = "bs_name", nullable = false, unique = true)
    private String name;

    @Column(name = "bs_geo", nullable = false, columnDefinition = "geometry(Polygon, 4326)")
    private Polygon geo;

    protected StopEntity() {
    }

    public StopEntity(String name, Polygon geo) {
        this.name = name;
        this.geo = geo;
    }

    public String getName() {
        return name;
    }

    public Polygon getGeo() {
        return geo;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setGeo(Polygon geo) {
        this.geo = geo;
    }
}
