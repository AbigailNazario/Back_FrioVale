package com.pi.frioVale.service;

import com.pi.frioVale.domain.User;
import com.pi.frioVale.dto.LoginChallengeResponse;
import com.pi.frioVale.dto.LoginRequest;
import com.pi.frioVale.dto.RegisterRequest;
import com.pi.frioVale.dto.UserResponse;
import com.pi.frioVale.exception.BusinessRuleException;
import com.pi.frioVale.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Regras de autenticação: login, cadastro e emissão do token JWT.
 *
 * [Seguranca - 2FA] A senha correta NAO entrega mais o token de acesso: devolve apenas o
 * desafio do segundo fator ({@link LoginChallengeResponse}). O token definitivo so sai do
 * {@link TwoFactorService} depois de um codigo TOTP ou de recuperacao valido.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final TwoFactorService twoFactorService;

    @Transactional(readOnly = true)
    public LoginChallengeResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(AuthService::invalidCredentials);

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw invalidCredentials();
        }

        return twoFactorService.challenge(user); // [Seguranca - 2FA] falta o segundo fator
    }

    @Transactional
    public LoginChallengeResponse register(RegisterRequest request) {
        UserResponse created = userService.register(request);
        User user = userRepository.findById(created.id())
                .orElseThrow(AuthService::invalidCredentials);
        // [Seguranca - 2FA] o cadastro tambem nao entrega acesso: a conta nova precisa cadastrar o autenticador
        return twoFactorService.challenge(user);
    }

    private static BusinessRuleException invalidCredentials() {
        return new BusinessRuleException("E-mail ou senha inválidos", HttpStatus.UNAUTHORIZED);
    }
}
