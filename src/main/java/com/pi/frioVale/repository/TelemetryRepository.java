package com.pi.frioVale.repository;

import com.pi.frioVale.domain.Telemetry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TelemetryRepository extends JpaRepository<Telemetry, Long> {

    List<Telemetry> findByTripIdOrderByTimestampAsc(Long tripId);

    Optional<Telemetry> findFirstByTripIdOrderByTimestampDesc(Long tripId);

    List<Telemetry> findTop2ByTripIdOrderByTimestampAsc(Long tripId);

    boolean existsByTripIdAndSourceEntryId(Long tripId, Long sourceEntryId);

    long countByTripId(Long tripId);

    @Query("""
            select count(t) from Telemetry t
            where t.trip.id = :tripId
              and t.temperature >= :minTemp
              and t.temperature <= :maxTemp
            """)
    long countInRangeByTripId(@Param("tripId") Long tripId,
                              @Param("minTemp") Double minTemp,
                              @Param("maxTemp") Double maxTemp);

    /** Busca leituras de telemetria que ainda não possuem resultado de IA para uma viagem específica. */
    @Query("""
            select t from Telemetry t
            where t.trip.id = :tripId
              and not exists (
                select i from IaPredictionResult i where i.telemetry.id = t.id
            )
            order by t.timestamp asc
        """)
    List<Telemetry> findTelemetryWithoutIaPrediction(@Param("tripId") Long tripId);

    /** Busca leituras de telemetria que ainda não possuem resultado de IA, limitada por quantidade. */
    @Query("""
            select t from Telemetry t
            where not exists (
                select i from IaPredictionResult i where i.telemetry.id = t.id
            )
            order by t.timestamp asc
        """)
    List<Telemetry> findTelemetryWithoutIaPrediction(@Param("limit") int limit);
}
