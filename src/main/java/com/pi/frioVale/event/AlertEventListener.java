package com.pi.frioVale.event;

import com.pi.frioVale.dto.AlertNotificationDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class AlertEventListener {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAlertCreated(AlertCreatedEvent event) {
        AlertNotificationDTO alert = event.payload();
        try {
            // Motorista/gestor acompanhando uma viagem específica
            messagingTemplate.convertAndSend("/topic/trips/" + alert.tripId() + "/alerts", alert);
            // Dashboard do gestor com visão de todas as viagens
            messagingTemplate.convertAndSend("/topic/alerts", alert);
        } catch (RuntimeException ex) {
            log.error("Falha ao enviar alerta {} via WebSocket: {}", alert.alertId(), ex.getMessage(), ex);
        }
    }
}
