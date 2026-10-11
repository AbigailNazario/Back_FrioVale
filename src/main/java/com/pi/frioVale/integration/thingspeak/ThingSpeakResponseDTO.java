package com.pi.frioVale.integration.thingspeak;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Resposta de GET /channels/{id}/feeds.json?results=1
 * Mapeamento dos campos do ESP32: field1=temperatura, field2=umidade, field3=latitude, field4=longitude.
 * O ThingSpeak devolve os valores como String (ou null).
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
            String field4
    ) {
    }
}
