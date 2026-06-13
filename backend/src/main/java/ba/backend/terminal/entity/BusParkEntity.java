package ba.backend.terminal.entity;

import ba.backend.shared.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.locationtech.jts.geom.Polygon;

@Entity
@Table(name = "busnow_bus_park")
public class BusParkEntity extends BaseEntity {
    @Column(name = "bp_name", nullable = false, unique = true)
    private String name;

    @Column(name = "bp_geo", nullable = false, columnDefinition = "geometry(Polygon, 4326)")
    private Polygon polygon;

    protected BusParkEntity() {
    }

    public BusParkEntity(String name, Polygon polygon) {
        this.name = name;
        this.polygon = polygon;
    }

    public String getName() {
        return name;
    }

    public Polygon getPolygon() {
        return polygon;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPolygon(Polygon polygon) {
        this.polygon = polygon;
    }
}
