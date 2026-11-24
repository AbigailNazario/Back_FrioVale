package com.pi.frioVale.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.pi.frioVale.security.JwtTokenService;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Assinatura e validacao de tokens JWT (HS256) com a chave de friovale.jwt.secret. */
@Configuration
public class JwtConfig {

    @Bean
    public SecretKey jwtSecretKey(JwtProperties props) {
        byte[] bytes = props.secret() == null ? new byte[0] : props.secret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "Propriedade friovale.jwt.secret ausente ou curta demais (minimo 32 caracteres)");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        return NimbusJwtDecoder.withSecretKey(jwtSecretKey).macAlgorithm(MacAlgorithm.HS256).build();
    }

    /**
     * Le o claim "role" (ex.: ROLE_COMPANY) como authority, sem prefixo adicional.
     *
     * [Seguranca - 2FA] Tambem adiciona uma authority com o tipo do token:
     *  - TOKEN_ACCESS: token definitivo, emitido somente depois do segundo fator;
     *  - TOKEN_MFA: token intermediario (senha ok, falta o codigo). Nao recebe o papel.
     * Tokens sem a claim token_use (emitidos antes do 2FA) nao recebem nenhuma das duas e sao recusados.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("role");
        roles.setAuthorityPrefix("");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<GrantedAuthority> authorities = new ArrayList<>();
            String use = jwt.getClaimAsString(JwtTokenService.TOKEN_USE_CLAIM);
            if (JwtTokenService.TOKEN_USE_ACCESS.equals(use)) {
                authorities.add(new SimpleGrantedAuthority(TOKEN_ACCESS));
                authorities.addAll(roles.convert(jwt));
            } else if (JwtTokenService.TOKEN_USE_MFA.equals(use)) {
                authorities.add(new SimpleGrantedAuthority(TOKEN_MFA));
            }
            return authorities;
        });
        return converter;
    }

    public static final String TOKEN_ACCESS = "TOKEN_ACCESS";
    public static final String TOKEN_MFA = "TOKEN_MFA";
}
