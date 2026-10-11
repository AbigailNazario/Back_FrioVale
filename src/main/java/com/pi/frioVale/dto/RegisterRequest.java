package com.pi.frioVale.dto;

import com.pi.frioVale.domain.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @param role opcional; quando omitido, o usuario e criado como ROLE_DRIVER. */
public record RegisterRequest(
        @NotBlank(message = "O nome e obrigatorio") @Size(max = 120) String name,
        @NotBlank(message = "O e-mail e obrigatorio") @Email(message = "E-mail invalido") @Size(max = 160) String email,
        @NotBlank(message = "A senha e obrigatoria")
        @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres") String password,
        Role role
) {
}
