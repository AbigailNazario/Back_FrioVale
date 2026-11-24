package com.pi.frioVale.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** [Seguranca - 2FA] Codigo de recuperacao de uso unico (formato XXXXX-XXXXX). */
public record RecoveryCodeRequest(
        @NotBlank(message = "Informe o código de recuperação")
        @Size(max = 32, message = "Código de recuperação inválido") String recoveryCode
) {
}
