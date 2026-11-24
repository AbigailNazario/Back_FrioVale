package com.pi.frioVale.security;

import com.pi.frioVale.config.OtpProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * [Seguranca - 2FA] Cifra o segredo TOTP antes de gravar no banco (AES-256-GCM).
 *
 * Se o banco vazar, o atacante nao consegue gerar os codigos sem a chave, que fica
 * somente na variavel de ambiente FRIOVALE_OTP_ENCRYPTION_KEY (fora do repositorio).
 * Formato gravado: Base64( IV de 12 bytes || texto cifrado + tag GCM ).
 */
@Component
public class TotpSecretCipher {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public TotpSecretCipher(OtpProperties props) {
        String encoded = props.encryptionKey();
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalStateException(
                    "Propriedade friovale.otp.encryption-key ausente: defina FRIOVALE_OTP_ENCRYPTION_KEY (32 bytes em Base64)");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(encoded.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("friovale.otp.encryption-key deve estar em Base64");
        }
        if (bytes.length != 32) {
            throw new IllegalStateException("friovale.otp.encryption-key deve ter 32 bytes (AES-256) depois de decodificada");
        }
        this.key = new SecretKeySpec(bytes, "AES");
    }

    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length)
                    .put(iv).put(encrypted).array());
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Falha ao cifrar o segredo do 2FA", ex);
        }
    }

    public String decrypt(String stored) {
        try {
            byte[] all = Base64.getDecoder().decode(stored);
            ByteBuffer buffer = ByteBuffer.wrap(all);
            byte[] iv = new byte[IV_BYTES];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("Falha ao decifrar o segredo do 2FA (chave trocada?)", ex);
        }
    }
}
