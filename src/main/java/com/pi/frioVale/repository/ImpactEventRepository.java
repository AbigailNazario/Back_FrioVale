package com.pi.frioVale.repository;

import com.pi.frioVale.domain.ImpactEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ImpactEventRepository extends JpaRepository<ImpactEvent, Long> {

    /** Idempotência: a mesma entrada do canal nunca gera dois impactos. */
    boolean existsByChannelIdAndEntryId(String channelId, Long entryId);

    boolean existsByTripIdAndEntryId(Long tripId, Long entryId);

    List<ImpactEvent> findByTripIdOrderByOriginalTimestampAsc(Long tripId);

    List<ImpactEvent> findByTripIdAndEntryId(Long tripId, Long entryId);

    @Query("select i from ImpactEvent i where i.container.id = :containerId order by i.originalTimestamp asc")
    List<ImpactEvent> findByContainerIdOrderByOriginalTimestampAsc(@Param("containerId") Long containerId);

    Optional<ImpactEvent> findFirstByTripIdOrderByOriginalTimestampDesc(Long tripId);

    Optional<ImpactEvent> findTopByContainerIdOrderByOriginalTimestampDesc(Long containerId);

    long countByTripId(@Param("tripId") Long tripId);

    /** Maior pico de aceleração registrado na viagem. */
    @Query("select max(i.peakG) from ImpactEvent i where i.trip.id = :tripId")
    Optional<Double> maxPeakByTripId(@Param("tripId") Long tripId);

    /** Impactos mais recentes de todos os contêineres (para a tela de Logs IoT). */
    List<ImpactEvent> findTop100ByOrderByOriginalTimestampDesc();
}
