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
        LocalDateTime timestamp,
        @JsonProperty("isAlert") Boolean isAlert
) {
    public static TelemetryResponse from(Telemetry t) {
        return new TelemetryResponse(t.getId(), t.getTemperature(), t.getHumidity(),
                t.getLatitude(), t.getLongitude(), t.getTimestamp(), t.getIsAlert());
    }
}
