package com.pi.frioVale.dto;

import com.pi.frioVale.domain.enums.TripStatus;

import java.time.LocalDateTime;

/**
 * Visão consolidada de uma viagem para as telas do front:
 * dados cadastrais + faixa segura da fruta + última leitura e indicadores.
 */
public record TripMonitorResponse(
        Long id,
        Long containerId,
        String containerCode,
        Long driverId,
        String driverName,
        Long fruitId,
        String fruitName,
        Double minTemp,
        Double maxTemp,
        Double minHumidity,
        Double maxHumidity,
        String origin,
        String destination,
        TripStatus status,
        LocalDateTime startDate,
        LocalDateTime endDate,
        Double temperature,
        Double humidity,
        Double latitude,
        Double longitude,
        LocalDateTime lastReadingAt,
        Boolean inRange,
        long readings,
        long readingsInRange,
        Integer inRangePercent,
        // Resultados da IA (metrica de produto - deteccao de anomalia)
        Boolean iaAnomalia,
        String iaResultado,
        String iaMensagem,
        String iaPerfil,
        // Resultado das regras fixas (separado da IA)
        Boolean regrasAnomalia,
        Boolean iaDivergencia
) {
}
