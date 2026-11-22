package com.pi.frioVale.dto;

import com.pi.frioVale.domain.ThingSpeakLog;

import java.time.LocalDateTime;

/** Log de uma leitura recebida do canal ThingSpeak, sem expor a api_key. */
public record ThingSpeakLogResponse(
        Long id,
        Long containerId,
        String containerCode,
        String channelId,
        Long entryId,
        Double temperature,
        Double humidity,
        Double latitude,
        Double longitude,
        /** Pico do intervalo em g (field3). Pode ser null em leituras antigas. */
        Double peakG,
        /** Inclinação em graus (field4). Pode ser null em leituras antigas. */
        Double tiltDeg,
        /** Contador acumulado de impactos na sessão do ESP32 (field5). */
        Double sessionImpactCount,
        LocalDateTime recordedAt,
        LocalDateTime fetchedAt
) {
    public static ThingSpeakLogResponse from(ThingSpeakLog log) {
        return new ThingSpeakLogResponse(
                log.getId(),
                log.getContainer() == null ? null : log.getContainer().getId(),
                log.getContainer() == null ? null : log.getContainer().getCode(),
                log.getChannelId(),
                log.getEntryId(),
                log.getTemperature(),
                log.getHumidity(),
                log.getLatitude(),
                log.getLongitude(),
                log.getPeakG(),
                log.getTiltDeg(),
                log.getSessionImpactCount(),
                log.getRecordedAt(),
                log.getFetchedAt());
    }
}
