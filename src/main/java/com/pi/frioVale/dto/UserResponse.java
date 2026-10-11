package com.pi.frioVale.dto;

import com.pi.frioVale.domain.User;
import com.pi.frioVale.domain.enums.Role;

/** Nunca inclui a senha. */
public record UserResponse(Long id, String name, String email, Role role) {

    public static UserResponse from(User u) {
        return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getRole());
    }
}
