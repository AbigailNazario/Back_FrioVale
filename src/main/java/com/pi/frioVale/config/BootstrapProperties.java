package com.pi.frioVale.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Dados do primeiro gestor, criado na subida se ainda nao existir nenhum. */
@ConfigurationProperties(prefix = "friovale.bootstrap")
public record BootstrapProperties(
        @DefaultValue("Gestor") String adminName,
        String adminEmail,
        String adminPassword
) {
}
