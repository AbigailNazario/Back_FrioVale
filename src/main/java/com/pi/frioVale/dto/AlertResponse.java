package com.pi.frioVale.dto;

import com.pi.frioVale.domain.Alert;

import java.time.LocalDateTime;

public record AlertResponse(
        Long id,
        Long telemetryId,
        Double temperature,
        LocalDateTime telemetryTimestamp,
        String message,
        Boolean resolved,
        LocalDateTime createdAt
) {
    public static AlertResponse from(Alert a) {
        return new AlertResponse(a.getId(), a.getTelemetry().getId(), a.getTelemetry().getTemperature(),
                a.getTelemetry().getTimestamp(), a.getMessage(), a.getResolved(), a.getCreatedAt());
    }
}
