package com.pi.frioVale.service;

import com.pi.frioVale.domain.User;
import com.pi.frioVale.dto.LoginRequest;
import com.pi.frioVale.dto.LoginResponse;
import com.pi.frioVale.repository.UserRepository;
import com.pi.frioVale.security.JwtTokenService;
import com.pi.frioVale.security.JwtTokenService.IssuedToken;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        // Mesma mensagem para e-mail inexistente e senha errada (nao revela quais e-mails existem)
        User user = userRepository.findByEmailIgnoreCase(request.email().trim())
                .filter(u -> passwordEncoder.matches(request.password(), u.getPassword()))
                .orElseThrow(() -> new BadCredentialsException("E-mail ou senha invalidos"));

        IssuedToken issued = jwtTokenService.issue(user);
        return new LoginResponse(issued.token(), "Bearer", issued.expiresAt(),
                user.getId(), user.getName(), user.getEmail(), user.getRole());
    }
}
