package com.pi.frioVale.repository;

import com.pi.frioVale.domain.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    @Query("""
            select a from Alert a
            join fetch a.telemetry t
            where t.trip.id = :tripId
            order by a.createdAt desc
            """)
    List<Alert> findByTripId(@Param("tripId") Long tripId);
}
