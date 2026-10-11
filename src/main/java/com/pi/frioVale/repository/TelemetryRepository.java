package com.pi.frioVale.repository;

import com.pi.frioVale.domain.Telemetry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TelemetryRepository extends JpaRepository<Telemetry, Long> {

    List<Telemetry> findByTripIdOrderByTimestampAsc(Long tripId);

    boolean existsByTripIdAndSourceEntryId(Long tripId, Long sourceEntryId);
}
