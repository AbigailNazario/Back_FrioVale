package com.pi.frioVale.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record FruitRequest(
        @NotBlank(message = "O nome é obrigatório") String name,
        @NotNull(message = "minTemp é obrigatório") Double minTemp,
        @NotNull(message = "maxTemp é obrigatório") Double maxTemp,
        @NotNull(message = "minHumidity é obrigatório")
        @DecimalMin("0.0") @DecimalMax("100.0") Double minHumidity,
        @NotNull(message = "maxHumidity é obrigatório")
        @DecimalMin("0.0") @DecimalMax("100.0") Double maxHumidity
) {
}
