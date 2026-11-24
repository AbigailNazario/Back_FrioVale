package com.pi.frioVale.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** [Seguranca - 2FA] Codigo de 6 digitos do aplicativo autenticador. */
public record OtpCodeRequest(
        @NotBlank(message = "Informe o código do aplicativo autenticador")
        @Pattern(regexp = "\\d{6}", message = "O código deve ter 6 dígitos") String code
) {
}
