package com.pi.frioVale.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param secret            chave HMAC (HS256) com no minimo 32 caracteres; em producao venha de variavel de ambiente
 * @param expirationMinutes validade do token (padrao 720 = 12 h, cobre uma viagem longa)
 */
@ConfigurationProperties(prefix = "friovale.jwt")
public record JwtProperties(
        String secret,
        @DefaultValue("720") long expirationMinutes
) {
}
