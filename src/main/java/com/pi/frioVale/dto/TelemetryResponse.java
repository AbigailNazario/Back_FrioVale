package com.pi.frioVale.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pi.frioVale.domain.Telemetry;

import java.time.LocalDateTime;

public record TelemetryResponse(
        Long id,
        Double temperature,
        Double humidity,
        Double latitude,
        Double longitude,
        /** Pico do intervalo em g (field3). */
        Double peakG,
        /** Inclinação em graus (field4). */
        Double tiltDeg,
        /** Contador acumulado de impactos na sessão do ESP32 (field5). */
        Double sessionImpactCount,
        LocalDateTime timestamp,
        @JsonProperty("isAlert") Boolean isAlert
) {
    public static TelemetryResponse from(Telemetry t) {
        return new TelemetryResponse(t.getId(), t.getTemperature(), t.getHumidity(),
                t.getLatitude(), t.getLongitude(), t.getPeakG(), t.getTiltDeg(),
                t.getSessionImpactCount(), t.getTimestamp(), t.getIsAlert());
    }
}
