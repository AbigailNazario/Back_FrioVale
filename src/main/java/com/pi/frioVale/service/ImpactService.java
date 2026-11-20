package com.pi.frioVale.service;

import com.pi.frioVale.config.ImpactProperties;
import com.pi.frioVale.domain.Container;
import com.pi.frioVale.domain.ImpactAlert;
import com.pi.frioVale.domain.ImpactEvent;
import com.pi.frioVale.domain.ThingSpeakLog;
import com.pi.frioVale.domain.Trip;
import com.pi.frioVale.dto.ImpactAlertNotificationDTO;
import com.pi.frioVale.dto.ImpactResponse;
import com.pi.frioVale.dto.ImpactSummaryResponse;
import com.pi.frioVale.event.ImpactAlertCreatedEvent;
import com.pi.frioVale.integration.thingspeak.ThingSpeakReading;
import com.pi.frioVale.repository.ImpactAlertRepository;
import com.pi.frioVale.repository.ImpactEventRepository;
import com.pi.frioVale.repository.ThingSpeakLogRepository;
import com.pi.frioVale.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Monitoramento de impactos do acelerômetro.
 *
 * <p>Eventos 800 (impacto), 840 (falha) e 841 (recuperação) chegam pelo mesmo canal
 * ThingSpeak do contêiner, misturados às leituras térmicas. O processamento é
 * idempotente por (channelId, entryId) e isolado do fluxo de telemetria/IA.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImpactService {

    private static final int EVENT_IMPACT = 800;
    private static final double MAX_VALID_PEAK_G = 100.0;
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    // Status do sensor:
    // "CALIBRATING" nunca é retornado porque o firmware só informa calibração via Serial,
    // sem enviar código dedicado ao ThingSpeak. Mantido documentado para o front.
    private static final String STATUS_NO_RECENT_DATA = "NO_RECENT_DATA";
    private static final String STATUS_ACTIVE_NO_IMPACTS = "ACTIVE_NO_IMPACTS";
    private static final String STATUS_ACTIVE_WITH_IMPACTS = "ACTIVE_WITH_IMPACTS";

    private final ImpactEventRepository impactRepository;
    private final ImpactAlertRepository impactAlertRepository;
    private final TripRepository tripRepository;
    private final ThingSpeakLogRepository thingSpeakLogRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final ImpactProperties impactProperties;

    /**
     * Processa as entradas de impacto de um lote de leituras.
     *
     * @return quantidade de novos ImpactEvents persistidos
     */
    @Transactional
    public int processReadings(Container container, List<ThingSpeakReading> readings) {
        if (container == null || readings == null || readings.isEmpty()) {
            return 0;
        }
        String channelId = container.getThingSpeakChannelId();
        if (channelId == null || channelId.isBlank()) {
            return 0;
        }
        String channel = channelId.trim();
        int saved = 0;

        for (ThingSpeakReading reading : readings) {
            if (reading == null || reading.eventCode() == null) {
                continue; // leitura térmica normal: não é evento
            }
            // 840/841 são apenas sinais de status do acelerômetro; já ficam nos logs brutos
            // (ThingSpeakLogService.saveReadings) e podem ser usados pelo front para status.
            if (reading.eventCode() != EVENT_IMPACT) {
                log.debug("Evento de status do acelerômetro {} (entry {}) registrado apenas como log bruto",
                        reading.eventCode(), reading.entryId());
                continue;
            }

            Double peak = reading.eventValue();
            if (!isValidPeak(peak)) {
                log.warn("Impacto inválido descartado (entry {}, pico {} g)", reading.entryId(), peak);
                continue;
            }

            // Idempotência: não duplica impacto nem alerta no reprocessamento.
            if (reading.entryId() != null
                    && impactRepository.existsByChannelIdAndEntryId(channel, reading.entryId())) {
                log.debug("Impacto da entry {} já processado; ignorando", reading.entryId());
                continue;
            }

            LocalDateTime occurredAt = reading.timestamp() != null
                    ? reading.timestamp()
                    : LocalDateTime.now(clock);
            LocalDateTime now = LocalDateTime.now(clock);

            Trip trip = findTripAt(container.getId(), occurredAt);

            ImpactEvent event = impactRepository.save(ImpactEvent.builder()
                    .trip(trip)
                    .container(container)
                    .channelId(channel)
                    .entryId(reading.entryId())
                    .plate(reading.plate())
                    .peakG(peak)
                    .originalTimestamp(occurredAt) // created_at do ThingSpeak = horário da ocorrência
                    .receivedAt(now)
                    .createdAt(now)
                    .build());
            saved++;

            if (impactProperties.alertEnabled() && peak >= impactProperties.thresholdG()) {
                createAlert(event, trip, peak, now);
            }
        }
        return saved;
    }

    private boolean isValidPeak(Double peak) {
        return peak != null
                && Double.isFinite(peak)
                && peak > 0
                && peak <= MAX_VALID_PEAK_G;
    }

    /**
     * Busca a viagem que estava em andamento no momento do impacto. Impactos recebidos
     * com atraso (timestamp antigo) são associados à viagem correta pelo intervalo
     * startDate..endDate, mesmo que o status atual já seja COMPLETED.
     */
    private Trip findTripAt(Long containerId, LocalDateTime moment) {
        if (containerId == null) {
            return null;
        }
        return tripRepository.findActiveAt(containerId, moment, PageRequest.of(0, 1))
                .stream().findFirst().orElse(null);
    }

    private void createAlert(ImpactEvent event, Trip trip, Double peak, LocalDateTime now) {
        if (impactAlertRepository.existsByImpactEventId(event.getId())) {
            log.debug("Alerta do impacto {} já existe; ignorando", event.getId());
            return;
        }

        String containerCode = event.getContainer().getCode();
        String message = buildMessage(peak, containerCode, event.getOriginalTimestamp());

        ImpactAlert alert = impactAlertRepository.save(ImpactAlert.builder()
                .impactEvent(event)
                .message(message)
                .resolved(false)
                .createdAt(now)
                .build());

        log.warn("ALERTA DE IMPACTO contêiner {}: {}", containerCode, message);

        Long tripId = trip == null ? null : trip.getId();
        eventPublisher.publishEvent(new ImpactAlertCreatedEvent(new ImpactAlertNotificationDTO(
                alert.getId(), event.getId(), tripId, containerCode, event.getPlate(),
                peak, message, event.getOriginalTimestamp())));
    }

    private String buildMessage(Double peak, String containerCode, LocalDateTime moment) {
        return String.format(PT_BR,
                "Impacto acima do limite configurado: %.1f g no contêiner %s às %s",
                peak, containerCode, moment.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
    }

    /** Resumo dos impactos de uma viagem (total, maior pico, último impacto e status do sensor). */
    @Transactional(readOnly = true)
    public ImpactSummaryResponse getSummary(Long tripId) {
        long total = impactRepository.countByTripId(tripId);
        Double maxPeak = impactRepository.maxPeakByTripId(tripId).orElse(null);
        ImpactResponse last = impactRepository.findFirstByTripIdOrderByOriginalTimestampDesc(tripId)
                .map(ImpactResponse::from).orElse(null);

        String status = resolveSensorStatus(tripId);
        return new ImpactSummaryResponse(total, maxPeak, last, status, messageFor(status));
    }

    /**
     * Infere o status do acelerômetro a partir do sinal mais recente (log bruto ou impacto).
     * O firmware não envia código de calibração ao ThingSpeak, então CALIBRATING nunca ocorre.
     */
    private String resolveSensorStatus(Long tripId) {
        LocalDateTime latestSignal = null;

        Optional<ThingSpeakLog> lastLog = thingSpeakLogRepository
                .findFirstByContainerIdOrderByRecordedAtDesc(tripId == null ? null : containerIdOfTrip(tripId));
        if (lastLog.isPresent()) {
            latestSignal = lastLog.get().getRecordedAt();
        }
        Optional<ImpactEvent> lastImpact = impactRepository
                .findFirstByTripIdOrderByOriginalTimestampDesc(tripId);
        if (lastImpact.isPresent()) {
            LocalDateTime t = lastImpact.get().getOriginalTimestamp();
            if (latestSignal == null || t.isAfter(latestSignal)) {
                latestSignal = t;
            }
        }

        LocalDateTime threshold = LocalDateTime.now(clock)
                .minusMinutes(impactProperties.staleAfterMinutes());
        if (latestSignal == null || latestSignal.isBefore(threshold)) {
            return STATUS_NO_RECENT_DATA;
        }
        return impactRepository.countByTripId(tripId) > 0
                ? STATUS_ACTIVE_WITH_IMPACTS
                : STATUS_ACTIVE_NO_IMPACTS;
    }

    /** O container de uma viagem é buscado via repositório de viagens (já carregado pelo findDetailedById). */
    private Long containerIdOfTrip(Long tripId) {
        return tripRepository.findDetailedById(tripId)
                .map(t -> t.getContainer() == null ? null : t.getContainer().getId())
                .orElse(null);
    }

    private String messageFor(String status) {
        return switch (status) {
            case STATUS_NO_RECENT_DATA -> "Sem dados recentes";
            case STATUS_ACTIVE_NO_IMPACTS -> "Nenhum impacto registrado";
            case STATUS_ACTIVE_WITH_IMPACTS -> null;
            default -> null;
        };
    }

    @Transactional(readOnly = true)
    public List<ImpactResponse> getByTrip(Long tripId) {
        return impactRepository.findByTripIdOrderByOriginalTimestampAsc(tripId).stream()
                .map(ImpactResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ImpactResponse> getByContainer(Long containerId) {
        return impactRepository.findByContainerIdOrderByOriginalTimestampAsc(containerId).stream()
                .map(ImpactResponse::from).toList();
    }

    /** Impactos mais recentes de todos os contêineres (tela de Logs IoT). */
    @Transactional(readOnly = true)
    public List<ImpactResponse> getRecent() {
        return impactRepository.findTop100ByOrderByOriginalTimestampDesc().stream()
                .map(ImpactResponse::from).toList();
    }

    /** Alertas de impacto mais recentes (tela de Alertas). */
    @Transactional(readOnly = true)
    public List<ImpactAlertNotificationDTO> getRecentAlerts() {
        return impactAlertRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .map(alert -> {
                    ImpactEvent event = alert.getImpactEvent();
                    Long tripId = event.getTrip() == null ? null : event.getTrip().getId();
                    return new ImpactAlertNotificationDTO(
                            alert.getId(), event.getId(), tripId,
                            event.getContainer() == null ? null : event.getContainer().getCode(),
                            event.getPlate(), event.getPeakG(), alert.getMessage(), alert.getCreatedAt());
                })
                .toList();
    }

    /** Duração configurada de "dados recentes" (exposta para testes/diagnóstico). */
    public Duration staleAfter() {
        return Duration.ofMinutes(impactProperties.staleAfterMinutes());
    }
}
