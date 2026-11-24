package com.pi.frioVale.controller;

import com.pi.frioVale.security.AccessGuard;
import com.pi.frioVale.dto.ImpactAlertNotificationDTO;
import com.pi.frioVale.dto.ImpactResponse;
import com.pi.frioVale.dto.ImpactSummaryResponse;
import com.pi.frioVale.dto.ThingSpeakSyncResponse;
import com.pi.frioVale.service.ImpactService;
import com.pi.frioVale.service.ThingSpeakLogService;
import com.pi.frioVale.service.TripService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** API de monitoramento de impactos do acelerômetro. */
@RestController
@RequestMapping("/api/impacts")
@RequiredArgsConstructor
public class ImpactController {

    private final ImpactService impactService;
    private final TripService tripService;
    private final ThingSpeakLogService thingSpeakLogService;
    private final AccessGuard accessGuard; // [Seguranca - IDOR]

    @GetMapping("/trips/{tripId}")
    public List<ImpactResponse> byTrip(@PathVariable Long tripId) {
        assertTripAccess(tripId);
        return impactService.getByTrip(tripId);
    }

    @GetMapping("/trips/{tripId}/summary")
    public ImpactSummaryResponse summary(@PathVariable Long tripId) {
        assertTripAccess(tripId);
        return impactService.getSummary(tripId);
    }

    @GetMapping("/containers/{containerId}")
    public List<ImpactResponse> byContainer(@PathVariable Long containerId) {
        accessGuard.requireContainer(containerId); // [Seguranca - IDOR]
        return impactService.getByContainer(containerId);
    }

    /** Impactos mais recentes de todos os contêineres (Logs IoT). */
    @GetMapping
    public List<ImpactResponse> recent() {
        return accessGuard.onlyOwnTrips(impactService.getRecent(), ImpactResponse::tripId); // [Seguranca - IDOR]
    }

    /** Alertas de impacto recentes (tela de Alertas). */
    @GetMapping("/alerts")
    public List<ImpactAlertNotificationDTO> recentAlerts() {
        return accessGuard.onlyOwnTrips(impactService.getRecentAlerts(), ImpactAlertNotificationDTO::tripId); // [Seguranca - IDOR]
    }

    /** Força uma varredura manual dos impactos no canal do contêiner. */
    @PostMapping("/sync")
    public ThingSpeakSyncResponse sync(
            @RequestParam Long containerId,
            @RequestParam(defaultValue = "100") int results) {
        accessGuard.requireContainer(containerId); // [Seguranca - IDOR] comando no dispositivo
        // Reaproveita a sincronização de logs (que já processa impactos por contêiner),
        // garantindo que os logs brutos e os impactos sejam gravados no mesmo fluxo.
        int saved = thingSpeakLogService.sync(containerId, Math.max(1, results));
        return new ThingSpeakSyncResponse(saved);
    }

    /** [Seguranca - IDOR] Checagem de dono centralizada no AccessGuard (mesma regra do STOMP, agora com log). */
    private void assertTripAccess(Long tripId) {
        accessGuard.requireTrip(tripId);
    }
}
