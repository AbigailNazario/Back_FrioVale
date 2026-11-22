package com.pi.frioVale.service;

import com.pi.frioVale.domain.Container;
import com.pi.frioVale.domain.ThingSpeakLog;
import com.pi.frioVale.dto.ThingSpeakLogResponse;
import com.pi.frioVale.exception.ResourceNotFoundException;
import com.pi.frioVale.integration.thingspeak.ThingSpeakIntegrationService;
import com.pi.frioVale.integration.thingspeak.ThingSpeakReading;
import com.pi.frioVale.repository.ContainerRepository;
import com.pi.frioVale.repository.ThingSpeakLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** Persiste e consulta os logs recebidos dos canais ThingSpeak (sistema IoT). */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThingSpeakLogService {

    private final ThingSpeakLogRepository logRepository;
    private final ContainerRepository containerRepository;
    private final ThingSpeakIntegrationService thingSpeak;
    private final Clock clock;
    private final ImpactService impactService;
    private final ImpactEventSyncService impactEventSyncService;

    @Transactional(readOnly = true)
    public List<ThingSpeakLogResponse> list(Long containerId) {
        List<ThingSpeakLog> logs = containerId == null
                ? logRepository.findTop500ByOrderByRecordedAtDesc()
                : logRepository.findTop500ByContainerIdOrderByRecordedAtDesc(containerId);
        return logs.stream().map(ThingSpeakLogResponse::from).toList();
    }

    /**
     * Grava as leituras recebidas do ThingSpeak, ignorando entradas já existentes.
     *
     * @return quantidade de logs novos
     */
    @Transactional
    public int saveReadings(Container container, List<ThingSpeakReading> readings) {
        if (container == null || readings == null || readings.isEmpty()) {
            return 0;
        }
        String channelId = container.getThingSpeakChannelId();
        if (!StringUtils.hasText(channelId)) {
            return 0;
        }
        String channel = channelId.trim();
        LocalDateTime fetchedAt = LocalDateTime.now(clock);
        int saved = 0;

        for (ThingSpeakReading reading : readings) {
            if (reading == null || reading.timestamp() == null) {
                continue;
            }
            ThingSpeakLog existente = reading.entryId() != null
                    ? logRepository.findByChannelIdAndEntryId(channel, reading.entryId()).orElse(null)
                    : null;
            if (existente != null) {
                // Backfill: preenche os novos campos de leituras já importadas, sem duplicar.
                boolean atualizado = false;
                if (existente.getPeakG() == null && reading.peakG() != null) {
                    existente.setPeakG(reading.peakG());
                    atualizado = true;
                }
                if (existente.getTiltDeg() == null && reading.tiltDeg() != null) {
                    existente.setTiltDeg(reading.tiltDeg());
                    atualizado = true;
                }
                if (existente.getSessionImpactCount() == null && reading.sessionImpactCount() != null) {
                    existente.setSessionImpactCount(reading.sessionImpactCount());
                    atualizado = true;
                }
                if (atualizado) {
                    logRepository.save(existente);
                    log.debug("Campos do acelerômetro preenchidos no log existente (entry {})", reading.entryId());
                }
                continue;
            }
            boolean alreadySaved = reading.entryId() == null
                    && logRepository.existsByChannelIdAndRecordedAt(channel, reading.timestamp());
            if (alreadySaved) {
                continue;
            }

            logRepository.save(ThingSpeakLog.builder()
                    .container(container)
                    .channelId(channel)
                    .entryId(reading.entryId())
                    .temperature(reading.temperature())
                    .humidity(reading.humidity())
                    .latitude(reading.latitude())
                    .longitude(reading.longitude())
                    .peakG(reading.peakG())
                    .tiltDeg(reading.tiltDeg())
                    .sessionImpactCount(reading.sessionImpactCount())
                    .recordedAt(reading.timestamp())
                    .fetchedAt(fetchedAt)
                    .build());
            saved++;
        }
        return saved;
    }

    /**
     * Consulta o ThingSpeak novamente e persiste os logs.
     * Se {@code containerId} for nulo, sincroniza todos os contêineres cadastrados.
     */
    @Transactional
    public int sync(Long containerId, int results) {
        List<Container> containers;
        if (containerId == null) {
            containers = containerRepository.findAll();
        } else {
            containers = List.of(containerRepository.findById(containerId)
                    .orElseThrow(() -> new ResourceNotFoundException("Contêiner", containerId)));
        }

        int totalSaved = 0;
        for (Container container : containers) {
            if (!StringUtils.hasText(container.getThingSpeakChannelId())) {
                log.debug("Contêiner {} sem canal ThingSpeak configurado", container.getCode());
                continue;
            }
            List<ThingSpeakReading> readings = thingSpeak.fetchRecentReadings(
                    container.getThingSpeakChannelId(), container.getThingSpeakApiKey(), results);
            totalSaved += saveReadings(container, readings);

            // Captura impactos atrasados mesmo sem viagem ativa (log bruto preservado).
            try {
                impactService.processReadings(container, readings);
            } catch (RuntimeException ex) {
                log.warn("Falha ao processar impactos do contêiner {} na sincronização: {}",
                        container.getCode(), ex.getMessage());
            }
        }
        // Eventos 800 do canal adicional (3551998) — isolado do fluxo principal.
        try {
            impactEventSyncService.sync();
        } catch (RuntimeException ex) {
            log.warn("Falha ao sincronizar eventos individuais do acelerômetro: {}", ex.getMessage());
        }
        return totalSaved;
    }
}
