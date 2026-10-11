package com.pi.frioVale.dto;

import com.pi.frioVale.domain.Alert;
import com.pi.frioVale.domain.Telemetry;
import com.pi.frioVale.domain.Trip;

import java.time.LocalDateTime;

/** Payload publicado em /topic/trips/{tripId}/alerts (PWA do motorista e dashboard do gestor). */
public record AlertNotificationDTO(
        Long alertId,
        Long tripId,
        String containerCode,
        String fruitName,
        Double temperature,
        Double minTemp,
        Double maxTemp,
        Double latitude,
        Double longitude,
        String message,
        LocalDateTime timestamp
) {
    public static AlertNotificationDTO of(Alert alert, Telemetry telemetry, Trip trip) {
        return new AlertNotificationDTO(
                alert.getId(), trip.getId(),
                trip.getContainer().getCode(), trip.getFruit().getName(),
                telemetry.getTemperature(), trip.getFruit().getMinTemp(), trip.getFruit().getMaxTemp(),
                telemetry.getLatitude(), telemetry.getLongitude(),
                alert.getMessage(), telemetry.getTimestamp());
    }
}
