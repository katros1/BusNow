package ba.backend.tracking.service;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;

@Service
public class GeofenceService {

    private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);

    public boolean contains(Polygon polygon, double latitude, double longitude) {
        Point point = GF.createPoint(new Coordinate(longitude, latitude));
        return polygon.contains(point);
    }
}
