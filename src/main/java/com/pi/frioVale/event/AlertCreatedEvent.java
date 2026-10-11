package com.pi.frioVale.event;

import com.pi.frioVale.dto.AlertNotificationDTO;

/** Publicado dentro da transação; o envio por WebSocket só ocorre após o commit. */
public record AlertCreatedEvent(AlertNotificationDTO payload) {
}
