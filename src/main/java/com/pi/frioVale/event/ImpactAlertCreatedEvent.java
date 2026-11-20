package com.pi.frioVale.event;

import com.pi.frioVale.dto.ImpactAlertNotificationDTO;

/** Publicado dentro da transação; o envio por WebSocket só ocorre após o commit. */
public record ImpactAlertCreatedEvent(ImpactAlertNotificationDTO payload) {
}
