package ba.backend.shared.geo;

import ba.backend.shared.dto.PolygonDto;
import java.util.ArrayList;
import java.util.List;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Component;

@Component
public class PolygonGeometryMapper {

    private static final int SRID = 4326;
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), SRID);

    public Polygon toPolygon(PolygonDto dto) {
        return toPolygon(dto.coordinates());
    }

    public Polygon toPolygon(List<List<Double>> source) {
        if (source == null || source.size() < 3) {
            throw new IllegalArgumentException("Polygon coordinates must contain at least 3 points.");
        }

        List<Coordinate> ringCoordinates = new ArrayList<>(source.size() + 1);
        for (List<Double> point : source) {
            if (point == null || point.size() != 2 || point.get(0) == null || point.get(1) == null) {
                throw new IllegalArgumentException("Each coordinate must contain exactly [longitude, latitude].");
            }
            ringCoordinates.add(new Coordinate(point.get(0), point.get(1)));
        }

        Coordinate first = ringCoordinates.getFirst();
        Coordinate last = ringCoordinates.getLast();
        if (!first.equals2D(last)) {
            ringCoordinates.add(new Coordinate(first.x, first.y));
        }

        if (ringCoordinates.size() < 4) {
            throw new IllegalArgumentException("Polygon ring must contain at least 4 coordinates.");
        }

        LinearRing shell = geometryFactory.createLinearRing(ringCoordinates.toArray(Coordinate[]::new));
        Polygon polygon = geometryFactory.createPolygon(shell);
        polygon.setSRID(SRID);

        if (!polygon.isValid()) {
            throw new IllegalArgumentException("Polygon is invalid.");
        }

        return polygon;
    }

    public List<List<Double>> toCoordinates(Polygon polygon) {
        Coordinate[] coordinates = polygon.getExteriorRing().getCoordinates();
        List<List<Double>> result = new ArrayList<>(coordinates.length);
        for (Coordinate coordinate : coordinates) {
            result.add(List.of(coordinate.getX(), coordinate.getY()));
        }
        return result;
    }
}
