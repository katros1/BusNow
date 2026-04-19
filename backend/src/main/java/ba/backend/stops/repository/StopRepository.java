package ba.backend.stops.repository;

import ba.backend.plan.repository.PlanNearestStopProjection;
import ba.backend.stops.entity.StopEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StopRepository extends JpaRepository<StopEntity, UUID>, JpaSpecificationExecutor<StopEntity> {
    @Query(
            value = """
                    SELECT
                        s.id AS stopId,
                        s.bs_name AS stopName,
                        ST_X(ST_Centroid(s.bs_geo)) AS longitude,
                        ST_Y(ST_Centroid(s.bs_geo)) AS latitude,
                        ST_DistanceSphere(
                            ST_Centroid(s.bs_geo),
                            ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)
                        ) / 1000.0 AS distanceKm
                    FROM iots_bus_stop s
                    ORDER BY ST_DistanceSphere(
                        ST_Centroid(s.bs_geo),
                        ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)
                    ) ASC
                    LIMIT 1
                    """,
            nativeQuery = true
    )
    PlanNearestStopProjection findNearestStop(
            @Param("longitude") double longitude,
            @Param("latitude") double latitude
    );
}
