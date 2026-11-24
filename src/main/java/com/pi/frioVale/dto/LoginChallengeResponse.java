package com.pi.frioVale.dto;

/**
 * [Seguranca - 2FA] Resposta do login/cadastro depois da senha correta.
 * NAO contem o token de acesso: apenas o token intermediario para concluir o segundo fator.
 *
 * @param mfaToken           token curto (token_use=mfa), aceito somente em /api/auth/2fa/*
 * @param enrollmentRequired true se o usuario ainda precisa cadastrar o aplicativo autenticador (QR Code)
 */
public record LoginChallengeResponse(
        String mfaToken,
        long expiresInMinutes,
        boolean enrollmentRequired
) {
}
