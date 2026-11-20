package com.pi.frioVale.dto;

/** Resumo dos impactos de uma viagem, exibido na aba Impactos e no PDF. */
public record ImpactSummaryResponse(
        long total,
        Double maxPeakG,
        ImpactResponse lastImpact,
        String sensorStatus,
        String message
) {
}
