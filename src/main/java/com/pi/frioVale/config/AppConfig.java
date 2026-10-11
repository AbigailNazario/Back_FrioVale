package com.pi.frioVale.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({ThingSpeakProperties.class, JwtProperties.class, BootstrapProperties.class})
public class AppConfig {

    /** Relogio unico da aplicacao (fuso configuravel), facilita testes e mantem datas consistentes. */
    @Bean
    public Clock clock(@Value("${friovale.timezone:America/Recife}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
