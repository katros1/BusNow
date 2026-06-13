package ba.backend.route.repository;

import ba.backend.plan.repository.PlanRoutePointProjection;
import ba.backend.route.entity.RouteEntity;
import ba.backend.route.entity.RouteDirection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RouteRepository extends JpaRepository<RouteEntity, UUID>, JpaSpecificationExecutor<RouteEntity> {

    @Override
    @EntityGraph(attributePaths = {"startBusPark", "endBusPark", "routeCode", "routeStops", "routeStops.stop"})
    List<RouteEntity> findAll();

    @EntityGraph(attributePaths = {"startBusPark", "endBusPark", "routeCode", "routeStops", "routeStops.stop"})
    Optional<RouteEntity> findById(UUID id);

    @EntityGraph(attributePaths = {"startBusPark", "endBusPark", "routeCode", "routeStops", "routeStops.stop"})
    List<RouteEntity> findByRouteCodeId(UUID routeCodeId);

    @EntityGraph(attributePaths = {"startBusPark", "endBusPark", "routeCode", "routeStops", "routeStops.stop"})
    Optional<RouteEntity> findByRouteCodeIdAndDirection(UUID routeCodeId, RouteDirection direction);

    @Query(
            value = """
                    SELECT
                        p.route_id AS routeId,
                        p.point_id AS pointId,
                        p.point_name AS pointName,
                        p.point_type AS pointType,
                        p.point_sequence AS pointSequence,
                        ST_X(p.point_geom) AS longitude,
                        ST_Y(p.point_geom) AS latitude,
                        ST_DistanceSphere(
                            p.point_geom,
                            ST_SetSRID(ST_MakePoint(:currentLongitude, :currentLatitude), 4326)
                        ) / 1000.0 AS walkToBoardingKm,
                        ST_DistanceSphere(
                            p.point_geom,
                            ST_SetSRID(ST_MakePoint(:destinationLongitude, :destinationLatitude), 4326)
                        ) / 1000.0 AS walkToDestinationKm
                    FROM (
                        SELECT
                            r.id AS route_id,
                            bp.id AS point_id,
                            bp.bp_name AS point_name,
                            'BUS_PARK' AS point_type,
                            0 AS point_sequence,
                            ST_Centroid(bp.bp_geo) AS point_geom
                        FROM busnow_route r
                        JOIN busnow_bus_park bp ON bp.id = r.rt_start_bus_park_id

                        UNION ALL

                        SELECT
                            rs.route_id AS route_id,
                            s.id AS point_id,
                            s.bs_name AS point_name,
                            'STOP' AS point_type,
                            rs.rs_sequence AS point_sequence,
                            ST_Centroid(s.bs_geo) AS point_geom
                        FROM busnow_route_stop rs
                        JOIN busnow_bus_stop s ON s.id = rs.stop_id

                        UNION ALL

                        SELECT
                            r.id AS route_id,
                            bp.id AS point_id,
                            bp.bp_name AS point_name,
                            'BUS_PARK' AS point_type,
                            COALESCE(mx.max_sequence, 0) + 1 AS point_sequence,
                            ST_Centroid(bp.bp_geo) AS point_geom
                        FROM busnow_route r
                        JOIN busnow_bus_park bp ON bp.id = r.rt_end_bus_park_id
                        LEFT JOIN (
                            SELECT route_id, MAX(rs_sequence) AS max_sequence
                            FROM busnow_route_stop
                            GROUP BY route_id
                        ) mx ON mx.route_id = r.id
                    ) p
                    ORDER BY p.route_id, p.point_sequence
                    """,
            nativeQuery = true
    )
    List<PlanRoutePointProjection> findRoutePointsForPlanning(
            @Param("currentLongitude") double currentLongitude,
            @Param("currentLatitude") double currentLatitude,
            @Param("destinationLongitude") double destinationLongitude,
            @Param("destinationLatitude") double destinationLatitude
    );
}
