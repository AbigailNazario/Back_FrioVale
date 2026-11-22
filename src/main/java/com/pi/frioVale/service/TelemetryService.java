package com.pi.frioVale.service;

import com.pi.frioVale.domain.Alert;
import com.pi.frioVale.domain.Fruit;
import com.pi.frioVale.domain.IaPredictionResult;
import com.pi.frioVale.domain.Telemetry;
import com.pi.frioVale.domain.Trip;
import com.pi.frioVale.domain.enums.TripStatus;
import com.pi.frioVale.dto.AlertNotificationDTO;
import com.pi.frioVale.dto.AlertResponse;
import com.pi.frioVale.dto.TelemetryResponse;
import com.pi.frioVale.event.AlertCreatedEvent;
import com.pi.frioVale.exception.ResourceNotFoundException;
import com.pi.frioVale.integration.thingspeak.ThingSpeakReading;
import com.pi.frioVale.repository.AlertRepository;
import com.pi.frioVale.repository.IaPredictionRepository;
import com.pi.frioVale.domain.ThingSpeakLog;
import com.pi.frioVale.repository.TelemetryRepository;
import com.pi.frioVale.repository.ThingSpeakLogRepository;
import com.pi.frioVale.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelemetryService {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private final TelemetryRepository telemetryRepository;
    private final AlertRepository alertRepository;
    private final TripRepository tripRepository;
    private final IaPredictionService iaPredictionService;
    private final IaPredictionRepository iaPredictionRepository;
    private final ThingSpeakLogRepository thingSpeakLogRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /**
     * Grava uma leitura do ThingSpeak, valida a temperatura contra a faixa da fruta da viagem
     * e, se estiver fora, marca isAlert, cria o Alert e dispara a notificação WebSocket (após o commit).
     *
     * @return a telemetria salva, ou vazio se a leitura foi descartada (duplicada, antiga ou viagem encerrada)
     */
    @Transactional
    public Optional<Telemetry> registerReading(Long tripId, ThingSpeakReading reading) {
        Trip trip = tripRepository.findDetailedById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Viagem", tripId));

        if (trip.getStatus() != TripStatus.IN_TRANSIT) {
            log.debug("Viagem {} não está mais IN_TRANSIT; leitura descartada", tripId);
            return Optional.empty();
        }
        // O poll devolve sempre a última entrada: se já foi gravada, apenas preenche
        // os campos do acelerômetro em leituras antigas (backfill), sem duplicar.
        if (reading.entryId() != null) {
            var existente = telemetryRepository.findByTripIdAndSourceEntryId(tripId, reading.entryId());
            if (existente.isPresent()) {
                Telemetry t = existente.get();
                boolean atualizado = false;
                if (t.getPeakG() == null && reading.peakG() != null) { t.setPeakG(reading.peakG()); atualizado = true; }
                if (t.getTiltDeg() == null && reading.tiltDeg() != null) { t.setTiltDeg(reading.tiltDeg()); atualizado = true; }
                if (t.getSessionImpactCount() == null && reading.sessionImpactCount() != null) {
                    t.setSessionImpactCount(reading.sessionImpactCount()); atualizado = true;
                }
                if (atualizado) {
                    telemetryRepository.save(t);
                    log.debug("Campos do acelerômetro preenchidos na telemetria existente (entry {})", reading.entryId());
                }
                return Optional.empty();
            }
        }
        // Ignora leitura anterior ao início da viagem (último dado do canal de antes da partida).
        if (trip.getStartDate() != null && reading.timestamp().isBefore(trip.getStartDate())) {
            return Optional.empty();
        }

        Fruit fruit = trip.getFruit();
        Double temp = reading.temperature();
        Double umidade = reading.humidity();

        // Regras fixas: temperatura fora da faixa
        boolean tempOutOfRange = false;
        if (temp != null) {
            boolean belowMinTemp = temp < fruit.getMinTemp();
            boolean aboveMaxTemp = temp > fruit.getMaxTemp();
            tempOutOfRange = belowMinTemp || aboveMaxTemp;
        }

        // Regras fixas: umidade fora da faixa (nova verificacao)
        boolean umidOutOfRange = false;
        if (umidade != null) {
            boolean belowMinHum = umidade < fruit.getMinHumidity();
            boolean aboveMaxHum = umidade > fruit.getMaxHumidity();
            umidOutOfRange = belowMinHum || aboveMaxHum;
        }

        boolean outOfRange = tempOutOfRange || umidOutOfRange;

        Telemetry telemetry = telemetryRepository.save(Telemetry.builder()
                .trip(trip)
                .temperature(reading.temperature())
                .humidity(reading.humidity())
                .latitude(reading.latitude())
                .longitude(reading.longitude())
                .peakG(reading.peakG())
                .tiltDeg(reading.tiltDeg())
                .sessionImpactCount(reading.sessionImpactCount())
                .timestamp(reading.timestamp())
                .sourceEntryId(reading.entryId())
                .isAlert(outOfRange)
                .build());

        if (outOfRange) {
            Alert alert = alertRepository.save(Alert.builder()
                    .telemetry(telemetry)
                    .message(buildMessage(trip, temp, tempOutOfRange, umidade, umidOutOfRange))
                    .resolved(false)
                    .createdAt(LocalDateTime.now(clock))
                    .build());

            log.warn("ALERTA viagem {}: {}", tripId, alert.getMessage());
            eventPublisher.publishEvent(new AlertCreatedEvent(AlertNotificationDTO.of(alert, telemetry, trip)));
        }

        // Analisar com IA em transacao separada (nao derruba o backend se a API Python estiver indisponível)
        // e nao mantem a transacao da telemetria aberta durante a chamada HTTP.
        try {
            processarIA(telemetry.getId(), tripId, temp, umidade, null, null, outOfRange);
        } catch (Exception ex) {
            log.warn("Falha ao processar IA para telemetria {}: {}", telemetry.getId(), ex.getMessage());
        }

        return Optional.of(telemetry);
    }

    /**
     * Processa IA para uma leitura em transacao separada.
     * Assim, a transacao da telemetria e confirmada antes de chamar a API Python,
     * evitando bloqueios prolongados no banco de dados.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processarIA(Long telemetryId, Long tripId, Double temperatura,
                            Double umidade, Double variacaoTemperatura, Double variacaoUmidade,
                            Boolean regrasAnomalia) {
        // Buscar leitura anterior para calcular variacoes (usar telemetria ja salva no banco)
        List<Telemetry> anteriores = telemetryRepository
                .findTop2ByTripIdOrderByTimestampAsc(tripId);
        Double varTemp = variacaoTemperatura;
        Double varUmid = variacaoUmidade;
        if (varTemp == null && anteriores != null && anteriores.size() >= 2) {
            Telemetry penultima = anteriores.get(anteriores.size() - 2);
            if (penultima.getTemperature() != null && temperatura != null) {
                varTemp = temperatura - penultima.getTemperature();
            }
        }
        if (varUmid == null && anteriores != null && anteriores.size() >= 2) {
            Telemetry penultima = anteriores.get(anteriores.size() - 2);
            if (penultima.getHumidity() != null && umidade != null) {
                varUmid = umidade - penultima.getHumidity();
            }
        }

        var iaResult = iaPredictionService.predictIa(
                tripId, temperatura, umidade, varTemp, varUmid
        );

        if (iaResult.isPresent()) {
            var ia = iaResult.get();
            Boolean iaAnomalia = ia.anomalia();
            Boolean divergencia = !regrasAnomalia.equals(iaAnomalia);

            IaPredictionResult iaPrediction = IaPredictionResult.builder()
                    .telemetry(telemetryRepository.findById(telemetryId).orElseThrow())
                    .iaAnomalia(iaAnomalia)
                    .iaResultado(ia.resultado())
                    .iaMensagem(ia.mensagem())
                    .iaPerfil(ia.perfil())
                    .regrasAnomalia(regrasAnomalia)
                    .divergencia(divergencia)
                    .analyzedAt(LocalDateTime.now(clock))
                    .build();
            iaPredictionRepository.save(iaPrediction);
        }
    }

    @Transactional(readOnly = true)
    public List<TelemetryResponse> getHistory(Long tripId) {
        assertTripExists(tripId);
        return telemetryRepository.findByTripIdOrderByTimestampAsc(tripId).stream()
                .map(TelemetryResponse::from).toList();
    }

    /**
     * Preenche os campos do acelerômetro em telemetrias já gravadas, casando cada
     * leitura com o log bruto do ThingSpeak pelo sourceEntryId (mesmo canal/entry_id).
     * Não cria duplicatas e preserva valores já preenchidos.
     *
     * @return quantidade de telemetrias atualizadas
     */
    @Transactional
    public int backfillAccelerometerFields(Long tripId) {
        Trip trip = tripRepository.findDetailedById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Viagem", tripId));
        if (trip.getContainer() == null || trip.getContainer().getThingSpeakChannelId() == null) {
            return 0;
        }
        String channelId = trip.getContainer().getThingSpeakChannelId().trim();
        var logs = thingSpeakLogRepository.findByChannelIdOrderByRecordedAtAsc(channelId);
        java.util.Map<Long, ThingSpeakLog> byEntry = new java.util.HashMap<>();
        for (ThingSpeakLog log : logs) {
            if (log.getEntryId() != null) byEntry.put(log.getEntryId(), log);
        }

        int updated = 0;
        for (Telemetry t : telemetryRepository.findByTripIdOrderByTimestampAsc(tripId)) {
            ThingSpeakLog log = t.getSourceEntryId() == null ? null : byEntry.get(t.getSourceEntryId());
            if (log == null) continue;
            boolean alterado = false;
            if (t.getPeakG() == null && log.getPeakG() != null) { t.setPeakG(log.getPeakG()); alterado = true; }
            if (t.getTiltDeg() == null && log.getTiltDeg() != null) { t.setTiltDeg(log.getTiltDeg()); alterado = true; }
            if (t.getSessionImpactCount() == null && log.getSessionImpactCount() != null) {
                t.setSessionImpactCount(log.getSessionImpactCount()); alterado = true;
            }
            if (alterado) { telemetryRepository.save(t); updated++; }
        }
        log.info("Backfill do acelerômetro na viagem {}: {} telemetria(s) atualizada(s)", tripId, updated);
        return updated;
    }

    @Transactional(readOnly = true)
    public List<AlertResponse> getAlerts(Long tripId) {
        assertTripExists(tripId);
        return alertRepository.findByTripId(tripId).stream().map(AlertResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AlertResponse> getAllAlerts() {
        return alertRepository.findAllDetailed(PageRequest.of(0, 200)).stream()
                .map(AlertResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AlertResponse> getRecentAlerts(int limit) {
        return alertRepository.findAllDetailed(PageRequest.of(0, Math.max(1, limit))).stream()
                .map(AlertResponse::from).toList();
    }

    private void assertTripExists(Long tripId) {
        if (!tripRepository.existsById(tripId)) {
            throw new ResourceNotFoundException("Viagem", tripId);
        }
    }

    private String buildMessage(Trip trip, Double temp, boolean tempOutOfRange, Double umidade, boolean umidOutOfRange) {
        Fruit fruit = trip.getFruit();
        StringBuilder msg = new StringBuilder();
        if (temp != null && tempOutOfRange) {
            boolean abaixo = temp < fruit.getMinTemp();
            msg.append(String.format(PT_BR,
                    "Temperatura %s da faixa segura: %.1f°C (%s: %.1f°C a %.1f°C) no contêiner %s",
                    abaixo ? "abaixo" : "acima",
                    temp, fruit.getName(), fruit.getMinTemp(), fruit.getMaxTemp(),
                    trip.getContainer().getCode()));
        }
        if (umidade != null && umidOutOfRange) {
            if (msg.length() > 0) {
                msg.append(". ");
            }
            boolean abaixo = umidade < fruit.getMinHumidity();
            msg.append(String.format(PT_BR,
                    "Umidade %s da faixa segura: %.1f%% (%s: %.1f%% a %.1f%%)",
                    abaixo ? "abaixo" : "acima",
                    umidade, fruit.getName(), fruit.getMinHumidity(), fruit.getMaxHumidity()));
        }
        return msg.toString();
    }
}
