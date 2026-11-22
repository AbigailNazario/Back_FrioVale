package com.pi.frioVale.integration.thingspeak;

import java.time.LocalDateTime;

/**
 * Leitura já convertida para tipos Java (resultado do mapeamento do DTO do ThingSpeak).
 *
 * <p>Leituras normais de telemetria preenchem temperature (field1), humidity (field2),
 * peakG (field3, pico do intervalo em g), tiltDeg (field4, inclinação em graus) e
 * sessionImpactCount (field5, contador acumulado de impactos da sessão do ESP32).
 * latitude/longitude são mantidos por compatibilidade com leituras antigas (que usavam
 * field3/field4 como coordenadas).</p>
 *
 * <p>Entradas de LOG de impacto (field1=800/840/841) preenchem eventCode, plate (field2)
 * e eventValue (pico em g, field5).</p>
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
        String plate,
        Double peakG,
        Double tiltDeg,
        Double sessionImpactCount
) {
    /** Construtor de compatibilidade: leitura de telemetria sem dados de acelerômetro. */
    public ThingSpeakReading(Long entryId, LocalDateTime timestamp, Double temperature,
                             Double humidity, Double latitude, Double longitude) {
        this(entryId, timestamp, temperature, humidity, latitude, longitude,
                null, null, null, null, null, null);
    }

    /** Construtor de telemetria com dados do acelerômetro (field3/field4/field5). */
    public ThingSpeakReading(Long entryId, LocalDateTime timestamp, Double temperature,
                             Double humidity, Double latitude, Double longitude,
                             Double peakG, Double tiltDeg, Double sessionImpactCount) {
        this(entryId, timestamp, temperature, humidity, latitude, longitude,
                null, null, null, peakG, tiltDeg, sessionImpactCount);
    }

    /** Construtor para entradas de evento (código + pico + placa). */
    public ThingSpeakReading(Long entryId, LocalDateTime timestamp, Integer eventCode,
                             Double eventValue, String plate) {
        this(entryId, timestamp, null, null, null, null,
                eventCode, eventValue, plate, null, null, null);
    }
}
