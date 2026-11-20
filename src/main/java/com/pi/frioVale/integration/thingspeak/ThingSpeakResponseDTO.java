package com.pi.frioVale.integration.thingspeak;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Resposta de GET /channels/{id}/feeds.json?results=1
 * Mapeamento dos campos do ESP32: field1=temperatura/ código de evento, field2=umidade/placa,
 * field3=latitude, field4=longitude, field5=pico em g (eventos de impacto).
 * O firmware publica eventos de impacto (800/840/841) no MESMO canal: nesses casos
 * field1 traz o código do evento e field5 o pico. O ThingSpeak devolve String (ou null).
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
