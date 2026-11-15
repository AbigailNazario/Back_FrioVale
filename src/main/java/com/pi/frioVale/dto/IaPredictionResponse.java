package com.pi.frioVale.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Resposta da API Python de IA para uma predicao de anomalia. */
public record IaPredictionResponse(
        @JsonProperty("anomalia") Boolean anomalia,
        @JsonProperty("resultado") String resultado,
        @JsonProperty("mensagem") String mensagem,
        @JsonProperty("tipo_fruta") String tipoFruta,
        @JsonProperty("perfil") String perfil,
        @JsonProperty("valores_recebidos") Object valoresRecebidos
) {
}
