package com.pi.frioVale.event;

import com.pi.frioVale.dto.ImpactAlertNotificationDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ImpactAlertEventListener {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onImpactAlertCreated(ImpactAlertCreatedEvent event) {
        ImpactAlertNotificationDTO alert = event.payload();
        try {
            if (alert.tripId() != null) {
                // Motorista/gestor acompanhando uma viagem específica
                messagingTemplate.convertAndSend(
                        "/topic/trips/" + alert.tripId() + "/impact-alerts", alert);
            }
            // Sempre publica no tópico global (mesmo quando o impacto não tem viagem,
            // o gestor ainda é avisado pelo código do contêiner).
            messagingTemplate.convertAndSend("/topic/alerts", alert);
        } catch (RuntimeException ex) {
            log.error("Falha ao enviar alerta de impacto {} via WebSocket: {}",
                    alert.alertId(), ex.getMessage(), ex);
        }
    }
}
