package com.pi.frioVale.integration.thingspeak;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * Cliente da REST API do ThingSpeak.
 * Tolerante a falhas: erro de rede/HTTP/JSON vira Optional.empty() + log, sem derrubar o agendador.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThingSpeakIntegrationService {

    private final RestClient thingSpeakRestClient;
    private final Clock clock;

    /** GET /channels/{channel_id}/feeds.json?api_key={api_key}&results=1 */
    public Optional<ThingSpeakResponseDTO> fetchLatest(String channelId, String apiKey) {
        try {
            ThingSpeakResponseDTO body = thingSpeakRestClient.get()
                    .uri(uri -> {
                        uri.path("/channels/{id}/feeds.json").queryParam("results", 1);
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

    /** Consulta o canal e já devolve a última leitura convertida; vazio se não houver dado utilizável. */
    public Optional<ThingSpeakReading> fetchLatestReading(String channelId, String apiKey) {
        return fetchLatest(channelId, apiKey)
                .map(ThingSpeakResponseDTO::feeds)
                .filter(feeds -> !feeds.isEmpty())
                .map(feeds -> feeds.get(feeds.size() - 1))
                .map(this::toReading)
                .filter(r -> r.temperature() != null);   // sem temperatura não há o que validar
    }

    private ThingSpeakReading toReading(ThingSpeakResponseDTO.Feed feed) {
        return new ThingSpeakReading(
                feed.entryId(),
                parseTimestamp(feed.createdAt()),
                parseDouble(feed.field1()),
                parseDouble(feed.field2()),
                parseDouble(feed.field3()),
                parseDouble(feed.field4()));
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
