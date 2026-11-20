package com.pi.frioVale.integration.thingspeak;

import java.time.LocalDateTime;

/**
 * Leitura já convertida para tipos Java (resultado do mapeamento do DTO do ThingSpeak).
 *
 * <p>Leituras normais de telemetria preenchem temperature/humidity/latitude/longitude.
 * Entradas de LOG de impacto (field1=800/840/841) preenchem eventCode, plate (em
 * {@code humidity}, compatível com o field2) e eventValue (pico em g, field5).</p>
 */
public record ThingSpeakReading(
        Long entryId,
        LocalDateTime timestamp,
        Double temperature,
        Double humidity,
        Double latitude,
        Double longitude,
        Integer eventCode,
        Double eventValue,
        String plate
) {
    /** Construtor de compatibilidade: usado por leituras de telemetria (sem evento). */
    public ThingSpeakReading(Long entryId, LocalDateTime timestamp, Double temperature,
                             Double humidity, Double latitude, Double longitude) {
        this(entryId, timestamp, temperature, humidity, latitude, longitude, null, null, null);
    }

    /** Construtor para entradas de evento (código + pico + placa). */
    public ThingSpeakReading(Long entryId, LocalDateTime timestamp, Integer eventCode,
                             Double eventValue, String plate) {
        this(entryId, timestamp, null, null, null, null, eventCode, eventValue, plate);
    }
}
