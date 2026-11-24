package com.pi.frioVale.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * [Seguranca] Configuracoes gerais dos controles.
 *
 * @param missionCode codigo de missao da equipe (6 caracteres enviado pelo professor).
 *                    Aparece em toda mensagem de log gerada quando um controle bloqueia algo.
 *                    Variavel de ambiente MISSION_CODE; o placeholder CODIGO_MISSAO deve ser substituido.
 */
@ConfigurationProperties(prefix = "friovale.security")
public record SecurityProperties(
        @DefaultValue("CODIGO_MISSAO") String missionCode
) {
}
