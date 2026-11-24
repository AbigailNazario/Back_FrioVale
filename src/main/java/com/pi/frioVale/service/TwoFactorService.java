package com.pi.frioVale.service;

import com.pi.frioVale.config.JwtProperties;
import com.pi.frioVale.config.OtpProperties;
import com.pi.frioVale.domain.RecoveryCode;
import com.pi.frioVale.domain.User;
import com.pi.frioVale.dto.AuthResponse;
import com.pi.frioVale.dto.LoginChallengeResponse;
import com.pi.frioVale.dto.RecoveryCodesResponse;
import com.pi.frioVale.dto.TotpConfirmResponse;
import com.pi.frioVale.dto.TotpSetupResponse;
import com.pi.frioVale.dto.UserResponse;
import com.pi.frioVale.exception.BusinessRuleException;
import com.pi.frioVale.repository.RecoveryCodeRepository;
import com.pi.frioVale.repository.UserRepository;
import com.pi.frioVale.security.JwtTokenService;
import com.pi.frioVale.security.SecurityEventLogger;
import com.pi.frioVale.security.TotpSecretCipher;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.exceptions.CodeGenerationException;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.util.Utils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * [Seguranca - 2FA] Segundo fator com TOTP (RFC 6238): cadastro por QR Code, verificacao do
 * codigo de 6 digitos, protecao contra replay, codigos de recuperacao de uso unico e bloqueio
 * temporario contra forca bruta.
 *
 * Toda a decisao acontece aqui, no servidor. O token de acesso definitivo so e emitido pelos
 * metodos confirm / verify / recover, depois de um segundo fator valido.
 */
@Service
@RequiredArgsConstructor
public class TwoFactorService {

    /** 32 simbolos sem caracteres ambiguos (0/O, 1/I/L): cada um carrega 5 bits. */
    private static final String RECOVERY_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int RECOVERY_LENGTH = 10;
    /** Quantos passos para tras ainda classificamos como "expirado" (apenas para o log). */
    private static final int EXPIRED_LOOKBACK_STEPS = 20;

    private static final String GENERIC_FAILURE = "Código inválido, expirado ou já utilizado";

    private final UserRepository userRepository;
    private final RecoveryCodeRepository recoveryCodeRepository;
    private final TotpSecretCipher cipher;
    private final OtpProperties props;
    private final JwtTokenService jwtTokenService;
    private final JwtProperties jwtProperties;
    private final SecurityEventLogger securityLog;
    private final Clock clock;

    private final SecureRandom random = new SecureRandom();
    private final SecretGenerator secretGenerator = new DefaultSecretGenerator(32); // 160 bits em Base32

    // ------------------------------------------------------------------ fluxo de login

    /** Depois da senha correta: devolve somente o token intermediario (sem acesso a API). */
    public LoginChallengeResponse challenge(User user) {
        JwtTokenService.IssuedToken mfa = jwtTokenService.issueMfa(user);
        return new LoginChallengeResponse(mfa.token(), props.mfaTokenMinutes(), !user.isTotpActive());
    }

    /** Passo 1 do cadastro: gera o segredo (cifrado no banco) e o QR Code. */
    @Transactional
    public TotpSetupResponse setup(Long userId) {
        User user = lockUser(userId);
        if (user.isTotpActive()) {
            // Sem esta regra, quem soubesse so a senha poderia recadastrar o 2FA no proprio celular.
            securityLog.otpBlocked(userId, "RECADASTRO_COM_2FA_ATIVO");
            throw new BusinessRuleException("O segundo fator já está ativo nesta conta", HttpStatus.CONFLICT);
        }

        String secret = secretGenerator.generate();
        user.setTotpSecretEnc(cipher.encrypt(secret));
        user.setTotpEnabled(false);
        user.setTotpLastStep(null);

        QrData data = new QrData.Builder()
                .label(user.getEmail())
                .secret(secret)
                .issuer(props.issuer())
                .algorithm(HashingAlgorithm.SHA1) // SHA1 e o unico aceito por todos os autenticadores
                .digits(props.digits())
                .period(props.periodSeconds())
                .build();
        try {
            ZxingPngQrGenerator qr = new ZxingPngQrGenerator();
            qr.setImageSize(240);
            String dataUri = Utils.getDataUriForImage(qr.generate(data), qr.getImageMimeType());
            securityLog.otpEvent(userId, "CADASTRO_INICIADO");
            return new TotpSetupResponse(dataUri, data.getUri(), props.issuer(), props.digits(), props.periodSeconds());
        } catch (QrGenerationException ex) {
            throw new IllegalStateException("Falha ao gerar o QR Code", ex);
        }
    }

    /** Passo 2 do cadastro: confirma o primeiro codigo, ativa o 2FA e entrega os codigos de recuperacao. */
    @Transactional(noRollbackFor = BusinessRuleException.class)
    public TotpConfirmResponse confirm(Long userId, String code) {
        User user = lockUser(userId);
        if (user.isTotpActive()) {
            securityLog.otpBlocked(userId, "CONFIRMACAO_COM_2FA_ATIVO");
            throw new BusinessRuleException("O segundo fator já está ativo nesta conta", HttpStatus.CONFLICT);
        }
        if (user.getTotpSecretEnc() == null) {
            throw new BusinessRuleException("Gere o QR Code antes de confirmar o código", HttpStatus.BAD_REQUEST);
        }

        checkTotp(user, code);
        user.setTotpEnabled(true);
        List<String> codes = replaceRecoveryCodes(user);
        securityLog.otpEvent(userId, "ATIVADO");
        return new TotpConfirmResponse(codes, accessToken(user));
    }

    /** Login normal: valida o codigo do aplicativo e so entao emite o token de acesso. */
    @Transactional(noRollbackFor = BusinessRuleException.class)
    public AuthResponse verify(Long userId, String code) {
        User user = lockUser(userId);
        requireActive(user);
        checkTotp(user, code);
        return accessToken(user);
    }

    /** Login com codigo de recuperacao: cada codigo funciona uma unica vez. */
    @Transactional(noRollbackFor = BusinessRuleException.class)
    public AuthResponse recover(Long userId, String recoveryCode) {
        User user = lockUser(userId);
        requireActive(user);
        ensureNotLocked(user);

        String normalized = normalizeRecovery(recoveryCode);
        Optional<RecoveryCode> found = normalized == null
                ? Optional.empty()
                : recoveryCodeRepository.findFirstByUserIdAndCodeHashAndUsedAtIsNull(userId, sha256(normalized));

        if (found.isEmpty()) {
            // Codigo inexistente OU ja consumido: mesma resposta, sem revelar qual dos dois.
            registerFailure(user, "RECUPERACAO_INVALIDA_OU_JA_USADA");
        }
        found.get().setUsedAt(LocalDateTime.now(clock)); // consome o codigo: nao vale mais
        resetFailures(user);
        recoveryCodeRepository.flush();
        long remaining = recoveryCodeRepository.countByUserIdAndUsedAtIsNull(userId);
        securityLog.otpEvent(userId, "RECUPERACAO_USADA restantes=" + remaining);
        return accessToken(user);
    }

    /** Gera novos codigos de recuperacao; todos os anteriores (usados ou nao) sao invalidados. */
    @Transactional
    public RecoveryCodesResponse regenerateRecoveryCodes(Long userId) {
        User user = lockUser(userId);
        requireActive(user);
        List<String> codes = replaceRecoveryCodes(user);
        securityLog.otpEvent(userId, "CODIGOS_RECUPERACAO_REGERADOS");
        return new RecoveryCodesResponse(codes);
    }

    // ------------------------------------------------------------------ regras do TOTP

    /**
     * Ponto em que o controle decide aceitar ou bloquear o codigo.
     *  1. bloqueio temporario por excesso de erros;
     *  2. o codigo precisa bater com o passo atual (+/- allowedDiscrepancy passos de 30 s);
     *  3. o passo aceito precisa ser MAIOR que o ultimo ja usado (anti-replay).
     */
    private void checkTotp(User user, String code) {
        ensureNotLocked(user);

        String secret = cipher.decrypt(user.getTotpSecretEnc());
        long currentStep = Instant.now(clock).getEpochSecond() / props.periodSeconds();
        int window = Math.max(0, props.allowedDiscrepancy());

        Long matchedStep = null;
        for (long step = currentStep - window; step <= currentStep + window; step++) {
            if (sameCode(generate(secret, step), code)) {
                matchedStep = step;
                break;
            }
        }

        if (matchedStep == null) {
            registerFailure(user, isRecentlyExpired(secret, code, currentStep - window) ? "CODIGO_EXPIRADO" : "CODIGO_INVALIDO");
        }
        Long lastStep = user.getTotpLastStep();
        if (lastStep != null && matchedStep <= lastStep) {
            registerFailure(user, "REPLAY");
        }

        user.setTotpLastStep(matchedStep);
        resetFailures(user);
    }

    /** So para o log: o codigo pertence a um passo logo antes da janela aceita? */
    private boolean isRecentlyExpired(String secret, String code, long oldestAccepted) {
        for (long step = oldestAccepted - 1; step >= oldestAccepted - EXPIRED_LOOKBACK_STEPS; step--) {
            if (sameCode(generate(secret, step), code)) {
                return true;
            }
        }
        return false;
    }

    private String generate(String secret, long step) {
        try {
            CodeGenerator generator = new DefaultCodeGenerator(HashingAlgorithm.SHA1, props.digits());
            return generator.generate(secret, step);
        } catch (CodeGenerationException ex) {
            throw new IllegalStateException("Falha ao gerar o código TOTP", ex);
        }
    }

    /** Comparacao em tempo constante (nao vaza por tempo de resposta quantos digitos acertou). */
    private static boolean sameCode(String expected, String informed) {
        if (informed == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                informed.trim().getBytes(StandardCharsets.UTF_8));
    }

    // ------------------------------------------------------------------ forca bruta

    private void ensureNotLocked(User user) {
        LocalDateTime lockedUntil = user.getOtpLockedUntil();
        if (lockedUntil != null && LocalDateTime.now(clock).isBefore(lockedUntil)) {
            securityLog.otpBlocked(user.getId(), "BLOQUEIO_TEMPORARIO");
            throw new BusinessRuleException(
                    "Muitas tentativas inválidas. Tente novamente em alguns minutos.", HttpStatus.TOO_MANY_REQUESTS);
        }
    }

    /** Conta o erro, bloqueia ao atingir o limite, registra o log e interrompe com 401. */
    private void registerFailure(User user, String motivo) {
        int attempts = (user.getOtpFailedAttempts() == null ? 0 : user.getOtpFailedAttempts()) + 1;
        if (attempts >= props.maxAttempts()) {
            user.setOtpFailedAttempts(0);
            user.setOtpLockedUntil(LocalDateTime.now(clock).plusMinutes(props.lockMinutes()));
            securityLog.otpBlocked(user.getId(), motivo + " (limite atingido: bloqueado por " + props.lockMinutes() + " min)");
        } else {
            user.setOtpFailedAttempts(attempts);
            securityLog.otpBlocked(user.getId(), motivo);
        }
        userRepository.save(user);
        throw new BusinessRuleException(GENERIC_FAILURE, HttpStatus.UNAUTHORIZED);
    }

    private static void resetFailures(User user) {
        user.setOtpFailedAttempts(0);
        user.setOtpLockedUntil(null);
    }

    // ------------------------------------------------------------------ codigos de recuperacao

    private List<String> replaceRecoveryCodes(User user) {
        recoveryCodeRepository.deleteAllByUserId(user.getId());
        LocalDateTime now = LocalDateTime.now(clock);
        List<String> plain = new ArrayList<>();
        for (int i = 0; i < props.recoveryCodeCount(); i++) {
            String code = randomRecoveryCode();
            plain.add(code.substring(0, 5) + "-" + code.substring(5));
            recoveryCodeRepository.save(RecoveryCode.builder()
                    .user(user)
                    .codeHash(sha256(code))
                    .createdAt(now)
                    .build());
        }
        return plain;
    }

    private String randomRecoveryCode() {
        StringBuilder sb = new StringBuilder(RECOVERY_LENGTH);
        for (int i = 0; i < RECOVERY_LENGTH; i++) {
            sb.append(RECOVERY_ALPHABET.charAt(random.nextInt(RECOVERY_ALPHABET.length())));
        }
        return sb.toString();
    }

    /** Aceita com ou sem hifen/espacos e em minusculas. */
    private static String normalizeRecovery(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        return cleaned.length() == RECOVERY_LENGTH ? cleaned : null;
    }

    /**
     * SHA-256 e suficiente aqui porque o codigo e aleatorio (~50 bits) e as tentativas sao
     * limitadas pelo bloqueio; permite buscar o codigo direto no banco.
     */
    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    // ------------------------------------------------------------------ auxiliares

    private User lockUser(Long userId) {
        return userRepository.findForUpdateById(userId)
                .orElseThrow(() -> new BusinessRuleException("Usuário não encontrado", HttpStatus.UNAUTHORIZED));
    }

    private void requireActive(User user) {
        if (!user.isTotpActive()) {
            securityLog.otpBlocked(user.getId(), "2FA_NAO_CADASTRADO");
            throw new BusinessRuleException("Cadastre o aplicativo autenticador para continuar", HttpStatus.FORBIDDEN);
        }
    }

    private AuthResponse accessToken(User user) {
        JwtTokenService.IssuedToken issued = jwtTokenService.issue(user);
        return AuthResponse.bearer(issued.token(), jwtProperties.expirationMinutes(), UserResponse.from(user));
    }
}
