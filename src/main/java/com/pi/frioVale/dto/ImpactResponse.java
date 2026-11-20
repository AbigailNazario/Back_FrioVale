package com.pi.frioVale.dto;

import com.pi.frioVale.domain.ImpactEvent;

import java.time.LocalDateTime;

/** Representação de um evento de impacto para a API/PWA. */
public record ImpactResponse(
        Long id,
        Long tripId,
        Long containerId,
        String containerCode,
        String plate,
        Double peakG,
        LocalDateTime originalTimestamp,
        LocalDateTime receivedAt,
        LocalDateTime createdAt
) {
    /** Deve ser chamado dentro de transação (acessa associações LAZY). */
    public static ImpactResponse from(ImpactEvent event) {
        return new ImpactResponse(
                event.getId(),
                event.getTrip() == null ? null : event.getTrip().getId(),
                event.getContainer() == null ? null : event.getContainer().getId(),
                event.getContainer() == null ? null : event.getContainer().getCode(),
                event.getPlate(),
                event.getPeakG(),
                event.getOriginalTimestamp(),
                event.getReceivedAt(),
                event.getCreatedAt());
    }
}
