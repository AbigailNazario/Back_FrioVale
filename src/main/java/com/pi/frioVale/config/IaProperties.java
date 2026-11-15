package com.pi.frioVale.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/** Propriedades de configuracao da API de IA (FastAPI Python). */
@ConfigurationProperties(prefix = "friovale.ia")
public record IaProperties(
        @DefaultValue("http://localhost:8000") String apiUrl,
        @DefaultValue("3s") Duration connectTimeout,
        @DefaultValue("5s") Duration readTimeout,
        @DefaultValue("true") boolean enabled
) {
}
