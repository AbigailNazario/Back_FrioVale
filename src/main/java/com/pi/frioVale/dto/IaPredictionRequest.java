package com.pi.frioVale.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Dados de entrada enviados para a API Python de IA. */
public record IaPredictionRequest(
        @JsonProperty("tipo_fruta") String tipoFruta,
        @JsonProperty("estagio_maturacao") String estagioMaturacao,
        @JsonProperty("temperatura_c") Double temperaturaC,
        @JsonProperty("umidade_relativa_pct") Double umidadeRelativaPct,
        @JsonProperty("variacao_temperatura_c") Double variacaoTemperaturaC,
        @JsonProperty("variacao_umidade_pct") Double variacaoUmidadePct
) {
}
