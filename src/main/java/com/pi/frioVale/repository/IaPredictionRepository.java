package com.pi.frioVale.repository;

import com.pi.frioVale.domain.IaPredictionResult;
import com.pi.frioVale.domain.Telemetry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IaPredictionRepository extends JpaRepository<IaPredictionResult, Long> {

    Optional<IaPredictionResult> findByTelemetryId(Long telemetryId);

    boolean existsByTelemetryId(Long telemetryId);
}
