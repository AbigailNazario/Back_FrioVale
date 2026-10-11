package com.pi.frioVale.integration.thingspeak;

import com.pi.frioVale.domain.Container;
import com.pi.frioVale.domain.Trip;
import com.pi.frioVale.service.TelemetryService;
import com.pi.frioVale.service.TripService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/** A cada 15 s consulta o ThingSpeak dos contêineres com viagem IN_TRANSIT e grava a telemetria. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ThingSpeakPollingScheduler {

    private final TripService tripService;
    private final ThingSpeakIntegrationService thingSpeak;
    private final TelemetryService telemetryService;

    @Scheduled(fixedRateString = "${friovale.thingspeak.poll-rate-ms:15000}")
    public void poll() {
        List<Trip> activeTrips = tripService.findActiveTripsForPolling();
        if (activeTrips.isEmpty()) {
            return;
        }
        log.debug("Consultando ThingSpeak para {} viagem(ns) ativa(s)", activeTrips.size());

        for (Trip trip : activeTrips) {
            try {
                pollTrip(trip);
            } catch (RuntimeException ex) {
                // Uma viagem com problema não pode impedir as demais de serem processadas.
                log.error("Erro ao processar viagem {}: {}", trip.getId(), ex.getMessage(), ex);
            }
        }
    }

    private void pollTrip(Trip trip) {
        Container container = trip.getContainer();
        if (!StringUtils.hasText(container.getThingSpeakChannelId())) {
            log.warn("Contêiner {} sem canal ThingSpeak configurado", container.getCode());
            return;
        }
        thingSpeak.fetchLatestReading(container.getThingSpeakChannelId(), container.getThingSpeakApiKey())
                .ifPresent(reading -> telemetryService.registerReading(trip.getId(), reading));
    }
}
