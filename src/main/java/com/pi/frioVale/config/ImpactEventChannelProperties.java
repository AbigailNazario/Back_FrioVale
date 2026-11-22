package com.pi.frioVale.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Canal ThingSpeak dedicado aos EVENTOS INDIVIDUAIS do acelerômetro (field1=800, field5=pico em g).
 *
 * <p>A telemetria e o pico/inclinação/contador (field3/field4/field5 do canal 3513340)
 * continuam sendo lidos pelo {@code container.thingSpeakChannelId} e pelo fluxo de
 * sincronização existente, que não foi alterado. Este canal é apenas uma fonte
 * ADICIONAL de eventos 800 para o resumo e a linha do tempo da viagem.</p>
 */
@ConfigurationProperties(prefix = "friovale.thingspeak.impact-events")
public record ImpactEventChannelProperties(
        @DefaultValue("") String channelId,
        @DefaultValue("") String apiKey
) {
}
