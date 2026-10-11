package com.pi.frioVale.service;

import com.pi.frioVale.domain.Alert;
import com.pi.frioVale.domain.Fruit;
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
import com.pi.frioVale.repository.TelemetryRepository;
import com.pi.frioVale.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
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
        // O poll devolve sempre a última entrada: ignora se já foi gravada.
        if (reading.entryId() != null
                && telemetryRepository.existsByTripIdAndSourceEntryId(tripId, reading.entryId())) {
            return Optional.empty();
        }
        // Ignora leitura anterior ao início da viagem (último dado do canal de antes da partida).
        if (trip.getStartDate() != null && reading.timestamp().isBefore(trip.getStartDate())) {
            return Optional.empty();
        }

        Fruit fruit = trip.getFruit();
        double temp = reading.temperature();
        boolean belowMin = temp < fruit.getMinTemp();
        boolean aboveMax = temp > fruit.getMaxTemp();
        boolean outOfRange = belowMin || aboveMax;

        Telemetry telemetry = telemetryRepository.save(Telemetry.builder()
                .trip(trip)
                .temperature(reading.temperature())
                .humidity(reading.humidity())
                .latitude(reading.latitude())
                .longitude(reading.longitude())
                .timestamp(reading.timestamp())
                .sourceEntryId(reading.entryId())
                .isAlert(outOfRange)
                .build());

        if (outOfRange) {
            Alert alert = alertRepository.save(Alert.builder()
                    .telemetry(telemetry)
                    .message(buildMessage(trip, temp, belowMin))
                    .resolved(false)
                    .createdAt(LocalDateTime.now(clock))
                    .build());

            log.warn("ALERTA viagem {}: {}", tripId, alert.getMessage());
            eventPublisher.publishEvent(new AlertCreatedEvent(AlertNotificationDTO.of(alert, telemetry, trip)));
        }
        return Optional.of(telemetry);
    }

    @Transactional(readOnly = true)
    public List<TelemetryResponse> getHistory(Long tripId) {
        assertTripExists(tripId);
        return telemetryRepository.findByTripIdOrderByTimestampAsc(tripId).stream()
                .map(TelemetryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AlertResponse> getAlerts(Long tripId) {
        assertTripExists(tripId);
        return alertRepository.findByTripId(tripId).stream().map(AlertResponse::from).toList();
    }

    private void assertTripExists(Long tripId) {
        if (!tripRepository.existsById(tripId)) {
            throw new ResourceNotFoundException("Viagem", tripId);
        }
    }

    private String buildMessage(Trip trip, double temp, boolean belowMin) {
        Fruit fruit = trip.getFruit();
        return String.format(PT_BR,
                "Temperatura %s da faixa segura: %.1f°C (%s: %.1f°C a %.1f°C) no contêiner %s",
                belowMin ? "abaixo" : "acima",
                temp, fruit.getName(), fruit.getMinTemp(), fruit.getMaxTemp(),
                trip.getContainer().getCode());
    }
}
