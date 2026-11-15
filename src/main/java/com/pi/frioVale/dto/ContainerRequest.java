package com.pi.frioVale.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param code               código identificador do contêiner (ex.: "FV001").
 * @param thingSpeakChannelId identificador do canal ThingSpeak do dispositivo IoT instalado.
 */
public record ContainerRequest(
        @NotBlank(message = "O código do contêiner é obrigatório")
        @Size(max = 40, message = "O código deve ter no máximo 40 caracteres")
        String code,

        @NotBlank(message = "O ID do canal ThingSpeak é obrigatório")
        @Size(max = 40, message = "O ID do canal deve ter no máximo 40 caracteres")
        String thingSpeakChannelId
) {
}
