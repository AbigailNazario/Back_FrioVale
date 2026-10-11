package com.pi.frioVale.dto;

import com.pi.frioVale.domain.enums.Role;

import java.time.Instant;

public record LoginResponse(
        String token,
        String tokenType,
        Instant expiresAt,
        Long userId,
        String name,
        String email,
        Role role
) {
}
