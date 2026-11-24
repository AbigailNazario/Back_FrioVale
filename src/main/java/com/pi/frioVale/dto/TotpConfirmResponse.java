package com.pi.frioVale.dto;

import java.util.List;

/**
 * [Seguranca - 2FA] Resultado da ativacao do 2FA: codigos de recuperacao (exibidos UMA vez)
 * e o token de acesso definitivo.
 */
public record TotpConfirmResponse(
        List<String> recoveryCodes,
        AuthResponse auth
) {
}
