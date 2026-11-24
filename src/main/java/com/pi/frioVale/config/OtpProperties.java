package com.pi.frioVale.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * [Seguranca - 2FA] Configuracao do segundo fator (TOTP, RFC 6238).
 *
 * @param issuer              nome exibido no aplicativo autenticador
 * @param periodSeconds       duracao de cada codigo (30 s e o padrao dos autenticadores)
 * @param digits              quantidade de digitos do codigo (6)
 * @param allowedDiscrepancy  passos de tolerancia para relogio adiantado/atrasado (1 = +/- 30 s).
 *                            0 aceita apenas o codigo do passo atual.
 * @param encryptionKey       chave AES-256 em Base64 (32 bytes) que cifra o segredo TOTP no banco.
 *                            Vem da variavel de ambiente FRIOVALE_OTP_ENCRYPTION_KEY; vazia = a aplicacao nao sobe.
 * @param maxAttempts         erros seguidos antes de bloquear o segundo fator do usuario
 * @param lockMinutes         tempo de bloqueio apos estourar maxAttempts
 * @param mfaTokenMinutes     validade do token intermediario (entre senha e codigo)
 * @param recoveryCodeCount   quantidade de codigos de recuperacao gerados
 */
@ConfigurationProperties(prefix = "friovale.otp")
public record OtpProperties(
        @DefaultValue("FrioVale") String issuer,
        @DefaultValue("30") int periodSeconds,
        @DefaultValue("6") int digits,
        @DefaultValue("1") int allowedDiscrepancy,
        String encryptionKey,
        @DefaultValue("5") int maxAttempts,
        @DefaultValue("5") long lockMinutes,
        @DefaultValue("5") long mfaTokenMinutes,
        @DefaultValue("10") int recoveryCodeCount
) {
}
