package com.pi.frioVale.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Propriedades do monitoramento de impactos do acelerômetro. */
@ConfigurationProperties(prefix = "friovale.impact")
public record ImpactProperties(
        /** Se alertas de impacto devem ser gerados. */
        @DefaultValue("true") boolean alertEnabled,
        /** Pico mínimo (em g) para gerar alerta. */
        @DefaultValue("2.0") Double thresholdG,
        /** Tempo sem sinal do sensor para considerar "sem dados recentes". */
        @DefaultValue("2") long staleAfterMinutes
) {
}
