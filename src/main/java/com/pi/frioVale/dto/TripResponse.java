package com.pi.frioVale.dto;

import com.pi.frioVale.domain.Trip;
import com.pi.frioVale.domain.enums.TripStatus;

import java.time.LocalDateTime;

public record TripResponse(
        Long id,
        Long containerId,
        String containerCode,
        Long driverId,
        String driverName,
        Long fruitId,
        String fruitName,
        TripStatus status,
        LocalDateTime startDate,
        LocalDateTime endDate
) {
    /** Deve ser chamado dentro de transação (acessa associações LAZY). */
    public static TripResponse from(Trip t) {
        return new TripResponse(
                t.getId(),
                t.getContainer().getId(), t.getContainer().getCode(),
                t.getDriver().getId(), t.getDriver().getName(),
                t.getFruit().getId(), t.getFruit().getName(),
                t.getStatus(), t.getStartDate(), t.getEndDate());
    }
}
