package com.pi.frioVale.service;

import com.pi.frioVale.domain.IaPredictionResult;
import com.pi.frioVale.domain.Telemetry;
import com.pi.frioVale.domain.Trip;
import com.pi.frioVale.dto.IaPredictionResponse;
import com.pi.frioVale.exception.ResourceNotFoundException;
import com.pi.frioVale.repository.IaPredictionRepository;
import com.pi.frioVale.repository.TelemetryRepository;
import com.pi.frioVale.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class IaReprocessingService {

    private final TelemetryRepository telemetryRepository;
    private final TripRepository tripRepository;
    private final IaPredictionRepository iaPredictionRepository;
    private final IaPredictionService iaPredictionService;
    private final Clock clock;

    /**
     * Reprocessa leituras de telemetria que ainda não possuem resultado de IA.
     * Executa automaticamente a cada 5 minutos (configurável).
     * Processa em lotes limitados para evitar sobrecarga.
     */
    @Scheduled(fixedRateString = "${friovale.ia.reprocess-rate-ms:300000}",
               initialDelayString = "${friovale.ia.reprocess-initial-delay-ms:60000}")
    public void reprocessPendingReadings() {
        List<Telemetry> pending = telemetryRepository.findTelemetryWithoutIaPrediction(50);
        if (pending.isEmpty()) {
            return;
        }
        log.info("Reprocessando {} leitura(ões) sem IA", pending.size());

        int processados = 0;
        int falhas = 0;

        for (Telemetry telemetry : pending) {
            try {
                processSingleReading(telemetry);
                processados++;
            } catch (Exception ex) {
                log.error("Falha ao reprocessar telemetria {}: {}", telemetry.getId(), ex.getMessage());
                falhas++;
            }
        }

        log.info("Reprocessamento concluído: {} processados, {} falhas", processados, falhas);
    }

    /**
     * Reprocessa uma leitura específica (endpoint manual).
     * Retorna true se processou, false se já existia previsão.
     */
    @Transactional
    public boolean reprocessTelemetry(Long telemetryId) {
        Telemetry telemetry = telemetryRepository.findById(telemetryId)
                .orElseThrow(() -> new ResourceNotFoundException("Telemetria", telemetryId));

        if (iaPredictionRepository.existsByTelemetryId(telemetryId)) {
            log.debug("Telemetria {} já possui previsão de IA", telemetryId);
            return false;
        }

        processSingleReading(telemetry);
        return true;
    }

    /**
     * Reprocessa SEMPRE a última leitura de uma viagem, atualizando seu resultado de IA.
     * Garante que a análise mais recente esteja disponível no painel mesmo quando
     * leituras antigas já foram processadas.
     */
    @Transactional
    public boolean reprocessarUltimaLeitura(Long tripId) {
        Telemetry ultima = telemetryRepository.findFirstByTripIdOrderByTimestampDesc(tripId)
                .orElse(null);
        if (ultima == null) {
            log.warn("Viagem {} não possui leituras para analisar", tripId);
            return false;
        }

        // Buscar as duas leituras mais recentes para calcular variações
        List<Telemetry> recentes = telemetryRepository
                .findTop2ByTripIdOrderByTimestampDesc(tripId);
        Double varTemp = null;
        Double varUmid = null;
        if (recentes != null && recentes.size() >= 2) {
            Telemetry atual = recentes.get(0);
            Telemetry anterior = recentes.get(1);
            if (anterior.getTemperature() != null && atual.getTemperature() != null) {
                varTemp = atual.getTemperature() - anterior.getTemperature();
            }
            if (anterior.getHumidity() != null && atual.getHumidity() != null) {
                varUmid = atual.getHumidity() - anterior.getHumidity();
            }
        }

        var iaResult = iaPredictionService.predictIa(
                tripId, ultima.getTemperature(), ultima.getHumidity(), varTemp, varUmid);
        if (iaResult.isEmpty()) {
            log.error("Falha ao obter predicao da IA para a ultima leitura da viagem {}", tripId);
            return false;
        }

        var ia = iaResult.get();
        iaPredictionRepository.findByTelemetryId(ultima.getId())
                .ifPresent(iaPredictionRepository::delete);
        iaPredictionRepository.flush();

        IaPredictionResult prediction = IaPredictionResult.builder()
                .telemetry(ultima)
                .iaAnomalia(ia.anomalia())
                .iaResultado(ia.resultado())
                .iaMensagem(ia.mensagem())
                .iaPerfil(ia.perfil())
                .regrasAnomalia(ultima.getIsAlert())
                .divergencia(!ultima.getIsAlert().equals(ia.anomalia()))
                .analyzedAt(LocalDateTime.now(clock))
                .build();
        iaPredictionRepository.save(prediction);
        iaPredictionRepository.flush();
        log.info("IA salva para ultima leitura {} da viagem {}: {}",
                ultima.getId(), tripId, ia.resultado());
        return true;
    }

    /**
     * Reprocessa todas as leituras pendentes de uma viagem específica.
     * Retorna a quantidade de leituras processadas.
     */
    @Transactional
    public int reprocessarTrip(Long tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Viagem", tripId));

        List<Telemetry> pendentes = telemetryRepository
                .findTelemetryWithoutIaPrediction(tripId);

        int processados = 0;
        for (Telemetry telemetry : pendentes) {
            if (telemetry.getTrip() != null && telemetry.getTrip().getId().equals(tripId)) {
                try {
                    processSingleReading(telemetry);
                    processados++;
                } catch (Exception ex) {
                    log.error("Falha ao reprocessar telemetria {}: {}", telemetry.getId(), ex.getMessage());
                }
            }
        }
        return processados;
    }

    private void processSingleReading(Telemetry telemetry) {
        Trip trip = telemetry.getTrip();
        if (trip == null) {
            log.warn("Telemetria {} sem viagem associada", telemetry.getId());
            return;
        }

        // Buscar as DUAS leituras mais recentes (a atual e a imediatamente anterior)
        // para calcular as variações corretamente.
        List<Telemetry> recentes = telemetryRepository
                .findTop2ByTripIdOrderByTimestampDesc(trip.getId());
        Double varTemp = null;
        Double varUmid = null;

        if (recentes != null && recentes.size() >= 2) {
            Telemetry atual = recentes.get(0);      // mais recente
            Telemetry anteriorLeitura = recentes.get(1); // imediatamente anterior
            if (anteriorLeitura.getTemperature() != null && atual.getTemperature() != null) {
                varTemp = atual.getTemperature() - anteriorLeitura.getTemperature();
            }
            if (anteriorLeitura.getHumidity() != null && atual.getHumidity() != null) {
                varUmid = atual.getHumidity() - anteriorLeitura.getHumidity();
            }
        }

        var iaResult = iaPredictionService.predictIa(
                trip.getId(),
                telemetry.getTemperature(),
                telemetry.getHumidity(),
                varTemp,
                varUmid
        );

        if (iaResult.isPresent()) {
            var ia = iaResult.get();
            Boolean regrasAnomalia = telemetry.getIsAlert();
            Boolean iaAnomalia = ia.anomalia();
            Boolean divergencia = !regrasAnomalia.equals(iaAnomalia);

            IaPredictionResult iaPrediction = IaPredictionResult.builder()
                    .telemetry(telemetry)
                    .iaAnomalia(iaAnomalia)
                    .iaResultado(ia.resultado())
                    .iaMensagem(ia.mensagem())
                    .iaPerfil(ia.perfil())
                    .regrasAnomalia(regrasAnomalia)
                    .divergencia(divergencia)
                    .analyzedAt(LocalDateTime.now(clock))
                    .build();
            iaPredictionRepository.save(iaPrediction);
            log.info("IA processada para telemetria {}: {}", telemetry.getId(), ia.resultado());
        }
    }
}