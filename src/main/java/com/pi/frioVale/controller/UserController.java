package com.pi.frioVale.controller;

import com.pi.frioVale.security.AccessGuard;
import com.pi.frioVale.domain.enums.Role;
import com.pi.frioVale.dto.UserRequest;
import com.pi.frioVale.dto.UserResponse;
import com.pi.frioVale.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AccessGuard accessGuard; // [Seguranca - IDOR]

    @GetMapping
    public List<UserResponse> list(@RequestParam(required = false) Role role) {
        return userService.findAll(role);
    }

    @GetMapping("/current")
    public UserResponse current(@AuthenticationPrincipal Jwt jwt) {
        return userService.current(Long.valueOf(jwt.getSubject()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody UserRequest request) {
        // [Seguranca - IDOR] contorno: sem isto o motorista criaria um gestor e passaria a ver todos os dispositivos
        accessGuard.requireCompany("user:create");
        return userService.create(request);
    }
}
