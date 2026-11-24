package com.pi.frioVale.dto;

/**
 * [Seguranca - 2FA] Dados para cadastrar o aplicativo autenticador.
 *
 * @param qrCodeDataUri imagem PNG do QR Code (data URI) para escanear
 * @param otpauthUri    mesmo conteudo do QR Code (otpauth://totp/...), para cadastro manual
 */
public record TotpSetupResponse(
        String qrCodeDataUri,
        String otpauthUri,
        String issuer,
        int digits,
        int periodSeconds
) {
}
