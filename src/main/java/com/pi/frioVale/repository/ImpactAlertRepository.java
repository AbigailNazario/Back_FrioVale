package com.pi.frioVale.repository;

import com.pi.frioVale.domain.ImpactAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ImpactAlertRepository extends JpaRepository<ImpactAlert, Long> {

    /** Idempotência: um alerta por evento de impacto. */
    boolean existsByImpactEventId(Long impactEventId);

    /** Alertas de impacto mais recentes (para a tela de Alertas). */
    List<ImpactAlert> findTop50ByOrderByCreatedAtDesc();

    long countByResolvedFalse();

    /** [Seguranca - IDOR] Alertas abertos apenas das viagens informadas (dashboard do motorista). */
    @org.springframework.data.jpa.repository.Query("select count(a) from ImpactAlert a where a.resolved = false and a.impactEvent.trip.id in :tripIds")
    long countOpenByTripIds(@org.springframework.data.repository.query.Param("tripIds") java.util.Collection<Long> tripIds);
}
