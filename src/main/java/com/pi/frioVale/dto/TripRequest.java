package com.pi.frioVale.dto;

import jakarta.validation.constraints.NotNull;

/**
 * @param startNow se {@code true} (padrão quando omitido) a viagem já nasce IN_TRANSIT;
 *                 se {@code false}, fica PLANNED até {@code POST /api/trips/{id}/start}.
 */
public record TripRequest(
        @NotNull(message = "containerId é obrigatório") Long containerId,
        @NotNull(message = "driverId é obrigatório") Long driverId,
        @NotNull(message = "fruitId é obrigatório") Long fruitId,
        Boolean startNow
) {
    public boolean shouldStartNow() {
        return startNow == null || startNow;
    }
}
