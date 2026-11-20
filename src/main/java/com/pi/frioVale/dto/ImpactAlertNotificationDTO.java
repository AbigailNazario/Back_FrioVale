package com.pi.frioVale.dto;

import java.time.LocalDateTime;

/** Payload publicado em /topic/trips/{tripId}/impact-alerts e /topic/alerts. */
public record ImpactAlertNotificationDTO(
        Long alertId,
        Long impactId,
        Long tripId,
        String containerCode,
        String plate,
        Double peakG,
        String message,
        LocalDateTime timestamp
) {
}
