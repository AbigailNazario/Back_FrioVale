package com.pi.frioVale.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/** Acesso ao usuario autenticado da requisicao HTTP atual (id = subject do JWT). */
@Component
public class CurrentUser {

    public Long id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return Long.valueOf(jwt.getSubject());
        }
        throw new AccessDeniedException("Usuario nao autenticado");
    }

    public boolean isCompany() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_COMPANY".equals(a.getAuthority()));
    }
}
