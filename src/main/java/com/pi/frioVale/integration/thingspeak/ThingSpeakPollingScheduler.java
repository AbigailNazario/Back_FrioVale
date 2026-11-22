package com.pi.frioVale.integration.thingspeak;

import com.pi.frioVale.domain.Container;
import com.pi.frioVale.domain.Trip;
import com.pi.frioVale.service.ImpactEventSyncService;
import com.pi.frioVale.service.ImpactService;
import com.pi.frioVale.service.TelemetryService;
import com.pi.frioVale.service.ThingSpeakLogService;
import com.pi.frioVale.service.TripService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/** A cada 15 s consulta o ThingSpeak dos contêineres com viagem IN_TRANSIT e grava logs + telemetria. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ThingSpeakPollingScheduler {

    /** Quantidade de entradas recentes buscadas por canal para alimentar a tela de logs. */
    private static final int RECENT_RESULTS = 20;

    private final TripService tripService;
    private final ThingSpeakIntegrationService thingSpeak;
    private final TelemetryService telemetryService;
    private final ThingSpeakLogService thingSpeakLogService;
    private final ImpactService impactService;
    private final ImpactEventSyncService impactEventSyncService;

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

    /**
     * Mantém a tela de logs alimentada mesmo quando não há viagem em andamento.
     * Consulta todos os contêineres cadastrados em um intervalo maior para
     * respeitar o limite de leituras do ThingSpeak.
     */
    @Scheduled(
            fixedRateString = "${friovale.thingspeak.log-sync-rate-ms:300000}",
            initialDelayString = "${friovale.thingspeak.log-sync-initial-delay-ms:30000}")
    public void syncAllLogs() {
        try {
            int saved = thingSpeakLogService.sync(null, RECENT_RESULTS);
            if (saved > 0) {
                log.debug("Sincronização periódica do ThingSpeak gravou {} novo(s) log(s)", saved);
            }
        } catch (RuntimeException ex) {
            log.error("Falha ao sincronizar logs do ThingSpeak: {}", ex.getMessage());
        }
        // Eventos individuais (canal adicional) — isolado: nunca interrompe a sincronização principal.
        try {
            int eventos = impactEventSyncService.sync();
            if (eventos > 0) {
                log.debug("Sincronização do canal de eventos gravou {} novo(s) impacto(s)", eventos);
            }
        } catch (RuntimeException ex) {
            log.warn("Falha ao sincronizar eventos individuais do acelerômetro: {}", ex.getMessage());
        }
    }

    private void pollTrip(Trip trip) {
        Container container = trip.getContainer();
        if (!StringUtils.hasText(container.getThingSpeakChannelId())) {
            log.warn("Contêiner {} sem canal ThingSpeak configurado", container.getCode());
            return;
        }

        List<ThingSpeakReading> readings = thingSpeak.fetchRecentReadings(
                container.getThingSpeakChannelId(), container.getThingSpeakApiKey(), RECENT_RESULTS);
        if (readings.isEmpty()) {
            return;
        }

        // Guarda o histórico bruto do IoT para a tela de logs.
        thingSpeakLogService.saveReadings(container, readings);

        // Processa impactos do acelerômetro isoladamente: uma falha aqui não derruba o poll
        // nem interfere na telemetria/IA térmica.
        try {
            impactService.processReadings(container, readings);
        } catch (RuntimeException ex) {
            log.error("Falha ao processar impactos do contêiner {}: {}",
                    container.getCode(), ex.getMessage(), ex);
        }

        // A telemetria da viagem usa a entrada mais recente que possui temperatura.
        readings.stream()
                .filter(reading -> reading.temperature() != null)
                .reduce((first, second) -> second)
                .ifPresent(reading -> telemetryService.registerReading(trip.getId(), reading));
    }
}
