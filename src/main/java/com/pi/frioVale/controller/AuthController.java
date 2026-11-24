package com.pi.frioVale.controller;

import com.pi.frioVale.dto.LoginChallengeResponse;
import com.pi.frioVale.dto.LoginRequest;
import com.pi.frioVale.dto.RegisterRequest;
import com.pi.frioVale.dto.UserResponse;
import com.pi.frioVale.service.AuthService;
import com.pi.frioVale.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    /** [Seguranca - 2FA] Senha correta devolve so o desafio do 2FA (mfaToken), nunca o token de acesso. */
    @PostMapping("/login")
    public LoginChallengeResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public LoginChallengeResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return userService.current(Long.valueOf(jwt.getSubject()));
    }
}
