package com.pi.frioVale.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "O e-mail e obrigatorio") String email,
        @NotBlank(message = "A senha e obrigatoria") String password
) {
}
