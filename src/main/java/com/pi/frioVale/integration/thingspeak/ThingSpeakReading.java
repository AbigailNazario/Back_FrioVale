package com.pi.frioVale.integration.thingspeak;

import java.time.LocalDateTime;

/** Leitura já convertida para tipos Java (resultado do mapeamento do DTO do ThingSpeak). */
public record ThingSpeakReading(
        Long entryId,
        LocalDateTime timestamp,
        Double temperature,
        Double humidity,
        Double latitude,
        Double longitude
) {
}
