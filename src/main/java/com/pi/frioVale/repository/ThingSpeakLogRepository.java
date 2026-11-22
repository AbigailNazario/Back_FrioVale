package com.pi.frioVale.repository;

import com.pi.frioVale.domain.ThingSpeakLog;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ThingSpeakLogRepository extends JpaRepository<ThingSpeakLog, Long> {

    @EntityGraph(attributePaths = "container")
    List<ThingSpeakLog> findTop500ByOrderByRecordedAtDesc();

    @EntityGraph(attributePaths = "container")
    List<ThingSpeakLog> findTop500ByContainerIdOrderByRecordedAtDesc(Long containerId);

    boolean existsByChannelIdAndEntryId(String channelId, Long entryId);

    /** Busca um log já importado para preencher campos novos sem criar duplicata. */
    Optional<ThingSpeakLog> findByChannelIdAndEntryId(String channelId, Long entryId);

    /** Todos os logs de um canal (usado no backfill de campos do acelerômetro). */
    List<ThingSpeakLog> findByChannelIdOrderByRecordedAtAsc(String channelId);

    boolean existsByChannelIdAndRecordedAt(String channelId, LocalDateTime recordedAt);

    /** Log bruto mais recente do contêiner (usado para inferir a atividade do acelerômetro). */
    @EntityGraph(attributePaths = "container")
    Optional<ThingSpeakLog> findFirstByContainerIdOrderByRecordedAtDesc(Long containerId);
}
