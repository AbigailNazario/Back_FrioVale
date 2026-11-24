package com.pi.frioVale.config;

import com.pi.frioVale.security.SecurityEventLogger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Segurança da API com JWT (HS256). Os beans de assinatura/validação do token
 * ficam em {@link JwtConfig}. Login e cadastro são públicos; as demais rotas
 * exigem um token válido.
 *
 * [Seguranca - 2FA] "Token valido" agora significa token de ACESSO (token_use=access),
 * emitido somente depois do segundo fator.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            SecurityEventLogger securityLog) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/auth/register",
                                "/api/ia/health",
                                "/ws/**",
                                "/error"
                        ).permitAll()
                        // [Seguranca - 2FA] Etapa do segundo fator: so o token intermediario (senha ok, falta o codigo)
                        .requestMatchers(
                                "/api/auth/2fa/setup",
                                "/api/auth/2fa/confirm",
                                "/api/auth/2fa/verify",
                                "/api/auth/2fa/recovery"
                        ).hasAuthority(JwtConfig.TOKEN_MFA)
                        // [Seguranca - 2FA] Todo o resto exige o token de acesso, emitido so depois do 2FA.
                        // Token intermediario ou token antigo (sem token_use) recebe 403 aqui.
                        .anyRequest().hasAuthority(JwtConfig.TOKEN_ACCESS))
                .exceptionHandling(ex -> ex.accessDeniedHandler(accessDeniedHandler(securityLog)))
                .oauth2ResourceServer(oauth -> oauth.jwt(
                        jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));
        return http.build();
    }

    /** [Seguranca - 2FA] Registra (com o codigo de missao) a tentativa de usar a API sem concluir o 2FA. */
    private AccessDeniedHandler accessDeniedHandler(SecurityEventLogger securityLog) {
        return (request, response, denied) -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof JwtAuthenticationToken jwtAuth) {
                boolean mfa = jwtAuth.getAuthorities().stream()
                        .anyMatch(a -> JwtConfig.TOKEN_MFA.equals(a.getAuthority()));
                boolean access = jwtAuth.getAuthorities().stream()
                        .anyMatch(a -> JwtConfig.TOKEN_ACCESS.equals(a.getAuthority()));
                Long userId = parseId(jwtAuth.getToken().getSubject());
                if (mfa) {
                    securityLog.otpBlocked(userId, "ROTA_PROTEGIDA_SEM_2FA");
                } else if (!access) {
                    securityLog.otpBlocked(userId, "TOKEN_SEM_TIPO_OU_ANTIGO");
                }
            }
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"status\":403,\"title\":\"Forbidden\",\"detail\":\"Acesso negado: conclua a verificação em duas etapas.\"}");
        };
    }

    private static Long parseId(String subject) {
        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${friovale.cors.allowed-origins:*}") List<String> origins) {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOriginPatterns(origins);
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
