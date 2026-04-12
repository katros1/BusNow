package ba.backend.shared.geo;

import java.util.ArrayList;
import java.util.List;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Component;

@Component
public class LineStringGeometryMapper {

    private static final int SRID = 4326;
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), SRID);

    public LineString toLineString(List<List<Double>> source) {
        if (source == null || source.size() < 2) {
            throw new IllegalArgumentException("LineString coordinates must contain at least 2 points.");
        }

        List<Coordinate> coordinates = new ArrayList<>(source.size());
        for (List<Double> point : source) {
            if (point == null || point.size() != 2 || point.get(0) == null || point.get(1) == null) {
                throw new IllegalArgumentException("Each coordinate must contain exactly [longitude, latitude].");
            }
            coordinates.add(new Coordinate(point.get(0), point.get(1)));
        }

        LineString lineString = geometryFactory.createLineString(coordinates.toArray(Coordinate[]::new));
        lineString.setSRID(SRID);

        if (!lineString.isValid()) {
            throw new IllegalArgumentException("LineString is invalid.");
        }

        return lineString;
    }

    public List<List<Double>> toCoordinates(LineString lineString) {
        Coordinate[] coordinates = lineString.getCoordinates();
        List<List<Double>> result = new ArrayList<>(coordinates.length);
        for (Coordinate coordinate : coordinates) {
            result.add(List.of(coordinate.getX(), coordinate.getY()));
        }
        return result;
    }
}
