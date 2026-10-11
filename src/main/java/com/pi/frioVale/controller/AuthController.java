package com.pi.frioVale.controller;

import com.pi.frioVale.dto.LoginRequest;
import com.pi.frioVale.dto.LoginResponse;
import com.pi.frioVale.dto.RegisterRequest;
import com.pi.frioVale.dto.UserResponse;
import com.pi.frioVale.service.AuthService;
import com.pi.frioVale.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /** Somente gestor (regra em SecurityConfig). Cria motorista por padrao. */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return userService.register(request);
    }
}
