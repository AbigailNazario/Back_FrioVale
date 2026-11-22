package com.pi.frioVale.integration.thingspeak;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Resposta de GET /channels/{id}/feeds.json?results=1
 * Mapeamento dos campos do ESP32 (mesmo canal):
 * - Leitura normal: field1=temperatura, field2=umidade, field3=pico do intervalo em g,
 *   field4=inclinação em graus, field5=contador acumulado de impactos da sessão.
 * - Evento de impacto (field1=800/840/841): field2=placa, field5=pico em g.
 * Latitude/longitude eram lidos de field3/field4 em firmware antigo (compatibilidade).
 * O ThingSpeak devolve String (ou null); valores ausentes viram null, zeros são preservados.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ThingSpeakResponseDTO(Channel channel, List<Feed> feeds) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Channel(Long id, String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Feed(
            @JsonProperty("created_at") String createdAt,
            @JsonProperty("entry_id") Long entryId,
            String field1,
            String field2,
            String field3,
            String field4,
            String field5
    ) {
    }
}
