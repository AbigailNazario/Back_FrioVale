package com.pi.frioVale.controller;

import com.pi.frioVale.dto.AuthResponse;
import com.pi.frioVale.dto.OtpCodeRequest;
import com.pi.frioVale.dto.RecoveryCodeRequest;
import com.pi.frioVale.dto.RecoveryCodesResponse;
import com.pi.frioVale.dto.TotpConfirmResponse;
import com.pi.frioVale.dto.TotpSetupResponse;
import com.pi.frioVale.service.TwoFactorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * [Seguranca - 2FA] Rotas do segundo fator.
 *
 * setup / confirm / verify / recovery: exigem o token intermediario (token_use=mfa), obtido no login.
 * recovery-codes/regenerate: exige o token de acesso (usuario ja logado com 2FA).
 * As regras de acesso por tipo de token ficam em SecurityConfig.
 * O usuario e sempre o "sub" do token validado pelo servidor, nunca um ID enviado no corpo.
 */
@RestController
@RequestMapping("/api/auth/2fa")
@RequiredArgsConstructor
public class TwoFactorController {

    private final TwoFactorService twoFactorService;

    @PostMapping("/setup")
    public TotpSetupResponse setup(@AuthenticationPrincipal Jwt jwt) {
        return twoFactorService.setup(userId(jwt));
    }

    @PostMapping("/confirm")
    public TotpConfirmResponse confirm(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody OtpCodeRequest request) {
        return twoFactorService.confirm(userId(jwt), request.code());
    }

    @PostMapping("/verify")
    public AuthResponse verify(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody OtpCodeRequest request) {
        return twoFactorService.verify(userId(jwt), request.code());
    }

    @PostMapping("/recovery")
    public AuthResponse recovery(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RecoveryCodeRequest request) {
        return twoFactorService.recover(userId(jwt), request.recoveryCode());
    }

    @PostMapping("/recovery-codes/regenerate")
    public RecoveryCodesResponse regenerate(@AuthenticationPrincipal Jwt jwt) {
        return twoFactorService.regenerateRecoveryCodes(userId(jwt));
    }

    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
