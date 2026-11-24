package com.pi.frioVale.repository;

import com.pi.frioVale.domain.Alert;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    @Query("""
            select a from Alert a
            join fetch a.telemetry t
            join fetch t.trip tr
            join fetch tr.container
            join fetch tr.fruit
            where t.trip.id = :tripId
            order by a.createdAt desc
            """)
    List<Alert> findByTripId(@Param("tripId") Long tripId);

    @Query("""
            select a from Alert a
            join fetch a.telemetry t
            join fetch t.trip tr
            join fetch tr.container
            join fetch tr.fruit
            order by a.createdAt desc
            """)
    List<Alert> findAllDetailed(Pageable pageable);

    long countByResolvedFalse();

    /** [Seguranca - IDOR] Alertas abertos apenas das viagens informadas (dashboard do motorista). */
    @org.springframework.data.jpa.repository.Query("select count(a) from Alert a where a.resolved = false and a.telemetry.trip.id in :tripIds")
    long countOpenByTripIds(@org.springframework.data.repository.query.Param("tripIds") java.util.Collection<Long> tripIds);
}
