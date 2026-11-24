package com.pi.frioVale.domain;

import com.pi.frioVale.domain.enums.Role;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

/** Usuário do sistema (motorista ou empresa/gestor). Tabela "users" para evitar conflito com palavra reservada. */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, unique = true, length = 160)
    private String email;

    @Column(length = 20)
    private String phone;

    /** Hash BCrypt, nunca a senha em texto puro. */
    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    // ---------------- [Seguranca - 2FA] ----------------

    /** Segredo TOTP cifrado com AES-GCM (nunca em texto puro, nunca exposto em DTO/log). */
    @Column(name = "totp_secret_enc", length = 255)
    private String totpSecretEnc;

    /** true depois que o usuario confirma o primeiro codigo do aplicativo autenticador. */
    @Column(name = "totp_enabled")
    private Boolean totpEnabled;

    /** Ultimo passo de tempo (epoch / 30 s) aceito: impede reutilizar um codigo (replay). */
    @Column(name = "totp_last_step")
    private Long totpLastStep;

    /** Erros seguidos no segundo fator (zera no sucesso). */
    @Column(name = "otp_failed_attempts")
    private Integer otpFailedAttempts;

    /** Segundo fator bloqueado ate este instante (forca bruta). */
    @Column(name = "otp_locked_until")
    private LocalDateTime otpLockedUntil;

    public boolean isTotpActive() {
        return Boolean.TRUE.equals(totpEnabled) && totpSecretEnc != null;
    }
}
