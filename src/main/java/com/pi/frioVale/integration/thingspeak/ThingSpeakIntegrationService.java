package com.pi.frioVale.integration.thingspeak;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Cliente da REST API do ThingSpeak.
 * Tolerante a falhas: erro de rede/HTTP/JSON vira lista vazia/Optional.empty() + log,
 * sem derrubar o agendador.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThingSpeakIntegrationService {

    /** Limite defensivo para o parâmetro results da API do ThingSpeak. */
    private static final int MAX_RESULTS = 8000;

    private final RestClient thingSpeakRestClient;
    private final Clock clock;

    /** GET /channels/{channel_id}/feeds.json?api_key={api_key}&results=1 */
    public Optional<ThingSpeakResponseDTO> fetchLatest(String channelId, String apiKey) {
        return fetch(channelId, apiKey, 1);
    }

    private Optional<ThingSpeakResponseDTO> fetch(String channelId, String apiKey, int results) {
        int safeResults = Math.max(1, Math.min(results, MAX_RESULTS));
        try {
            ThingSpeakResponseDTO body = thingSpeakRestClient.get()
                    .uri(uri -> {
                        uri.path("/channels/{id}/feeds.json").queryParam("results", safeResults);
                        if (StringUtils.hasText(apiKey)) {      // canais públicos dispensam a chave
                            uri.queryParam("api_key", "{key}");
                        }
                        return uri.build(Map.of("id", channelId, "key", apiKey == null ? "" : apiKey));
                    })
                    .retrieve()
                    .body(ThingSpeakResponseDTO.class);
            return Optional.ofNullable(body);
        } catch (RuntimeException ex) {
            // Não loga a URL para não vazar a api_key.
            log.warn("Falha ao consultar ThingSpeak (canal {}): {}", channelId, ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Busca as últimas {@code results} entradas do canal e já converte para tipos Java.
     * Ideal para alimentar a tela de logs do IoT.
     */
    public List<ThingSpeakReading> fetchRecentReadings(String channelId, String apiKey, int results) {
        List<ThingSpeakResponseDTO.Feed> feeds = fetch(channelId, apiKey, results)
                .map(ThingSpeakResponseDTO::feeds)
                .orElseGet(List::of);
        return feeds.stream().map(this::toReading).toList();
    }

    /** Consulta o canal e devolve a última leitura com temperatura; vazio se não houver dado utilizável. */
    public Optional<ThingSpeakReading> fetchLatestReading(String channelId, String apiKey) {
        List<ThingSpeakReading> readings = fetchRecentReadings(channelId, apiKey, 1);
        if (readings.isEmpty()) {
            return Optional.empty();
        }
        ThingSpeakReading latest = readings.get(readings.size() - 1);
        return latest.temperature() == null ? Optional.empty() : Optional.of(latest);
    }

    /**
     * Converte um feed. Quando field1 é um código de evento de impacto (800/840/841),
     * o valor NÃO é temperatura: ele vira eventCode, field2 vira plate e field5 o pico (g).
     * Para leituras normais, mantém o comportamento anterior (field1=temperatura etc.).
     */
    private ThingSpeakReading toReading(ThingSpeakResponseDTO.Feed feed) {
        Integer eventCode = parseEventCode(feed.field1());
        LocalDateTime timestamp = parseTimestamp(feed.createdAt());
        if (eventCode != null) {
            // field2 é a identificação da placa (texto), field5 o pico em g.
            return new ThingSpeakReading(
                    feed.entryId(), timestamp, eventCode,
                    parseDouble(feed.field5()),
                    StringUtils.hasText(feed.field2()) ? feed.field2().trim() : null);
        }
        // field3/field4: no firmware atual são pico (g) e inclinação (°).
        // Mantemos latitude/longitude por compatibilidade com leituras antigas.
        Double field3 = parseDouble(feed.field3());
        Double field4 = parseDouble(feed.field4());
        return new ThingSpeakReading(
                feed.entryId(),
                timestamp,
                parseDouble(feed.field1()),
                parseDouble(feed.field2()),
                field3,   // latitude (legado)
                field4,   // longitude (legado)
                field3,   // peakG
                field4,   // tiltDeg
                parseDouble(feed.field5())); // sessionImpactCount
    }

    /** Códigos de evento do acelerômetro publicados pelo firmware no field1. */
    private static final java.util.Set<Integer> IMPACT_EVENT_CODES =
            java.util.Set.of(800, 840, 841);

    private Integer parseEventCode(String value) {
        Integer code = parseInt(value);
        return code != null && IMPACT_EVENT_CODES.contains(code) ? code : null;
    }

    private Integer parseInt(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private LocalDateTime parseTimestamp(String createdAt) {
        try {
            // ThingSpeak envia ISO-8601 em UTC (ex.: 2026-10-10T15:04:05Z)
            return OffsetDateTime.parse(createdAt).atZoneSameInstant(clock.getZone()).toLocalDateTime();
        } catch (RuntimeException ex) {
            return LocalDateTime.now(clock);
        }
    }

    private Double parseDouble(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            double d = Double.parseDouble(value.trim().replace(',', '.'));
            return Double.isFinite(d) ? d : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
