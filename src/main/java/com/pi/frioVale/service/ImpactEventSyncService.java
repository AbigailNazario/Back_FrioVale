package com.pi.frioVale.service;

import com.pi.frioVale.config.ImpactEventChannelProperties;
import com.pi.frioVale.domain.Container;
import com.pi.frioVale.integration.thingspeak.ThingSpeakIntegrationService;
import com.pi.frioVale.integration.thingspeak.ThingSpeakReading;
import com.pi.frioVale.repository.ContainerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Importa EVENTOS INDIVIDUAIS de impacto (field1=800 com pico válido em field5)
 * do canal ThingSpeak de logs operacionais configurado em
 * {@code friovale.thingspeak.impact-events.*}.
 *
 * <p> Este serviço é ADICIONAL e isolado: a sincronização principal dos canais dos
 * contêineres (telemetria e leituras do acelerômetro field3/field4/field5) continua
 * funcionando exatamente como antes. Qualquer falha aqui é capturada pelo chamador
 * e apenas registrada em log, sem interromper a sincronização principal.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImpactEventSyncService {

    private static final int RECENT_RESULTS = 100;

    private final ImpactEventChannelProperties props;
    private final ThingSpeakIntegrationService thingSpeak;
    private final ContainerRepository containerRepository;
    private final ImpactService impactService;

    /**
     * Busca o canal de eventos e processa somente entradas com field1=800 e pico válido.
     *
     * @return quantidade de novos impactos persistidos
     */
    public int sync() {
        String channelId = props.channelId() == null ? "" : props.channelId().trim();
        if (channelId.isBlank()) {
            return 0;
        }

        List<ThingSpeakReading> readings = thingSpeak.fetchRecentReadings(
                channelId, props.apiKey(), RECENT_RESULTS);
        if (readings.isEmpty()) {
            return 0;
        }

        List<Container> containers = containerRepository.findAll();
        if (containers.isEmpty()) {
            log.debug("Nenhum contêiner cadastrado; eventos do canal {} ignorados", channelId);
            return 0;
        }

        int saved = 0;
        for (ThingSpeakReading reading : readings) {
            // Apenas eventos 800 com pico válido (o ImpactService revalida o pico).
            if (reading == null || reading.eventCode() == null || reading.eventValue() == null) {
                continue;
            }
            Container container = resolveContainer(reading.plate(), containers);
            if (container == null) {
                log.warn("Evento 800 (entry {}) do canal {} sem contêiner associado (placa='{}'); ignorado",
                        reading.entryId(), channelId, reading.plate());
                continue;
            }
            try {
                // channelId do canal de eventos evita colisão de entry_id com a telemetria.
                saved += impactService.processReadings(container, List.of(reading), channelId);
            } catch (RuntimeException ex) {
                log.error("Falha ao processar evento 800 (entry {}): {}", reading.entryId(), ex.getMessage());
            }
        }
        return saved;
    }

    /** Casa a placa (field2) com o código/id do contêiner; fallback para dispositivo único. */
    private Container resolveContainer(String plate, List<Container> containers) {
        if (StringUtils.hasText(plate)) {
            String alvo = plate.trim();
            for (Container c : containers) {
                if (alvo.equalsIgnoreCase(c.getCode())
                        || alvo.equals(String.valueOf(c.getId()))) {
                    return c;
                }
            }
        }
        return containers.size() == 1 ? containers.get(0) : null;
    }
}
