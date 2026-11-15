package com.pi.frioVale.dto;

import com.pi.frioVale.domain.enums.TripStatus;

/**
 * DTO para atualização de viagem.
 * @param status       novo status (obrigatório).
 * @param containerId  novo contêiner (opcional; se omitido, mantém o atual).
 * @param driverId     novo motorista (opcional; se omitido, mantém o atual).
 * @param fruitId      nova fruta (opcional; se omitido, mantém a atual).
 * @param origin       nova origem (opcional; se omitido, mantém a atual).
 * @param destination  novo destino (opcional; se omitido, mantém o atual).
 * @param startNow     se true e status for IN_TRANSIT, inicia a viagem agora.
 */
public record TripUpdateRequest(
        @jakarta.validation.constraints.NotNull(message = "O status é obrigatório") TripStatus status,
        Long containerId,
        Long driverId,
        Long fruitId,
        String origin,
        String destination,
        Boolean startNow
) {
}
