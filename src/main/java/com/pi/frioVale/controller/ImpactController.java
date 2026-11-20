package com.pi.frioVale.controller;

import com.pi.frioVale.dto.ImpactAlertNotificationDTO;
import com.pi.frioVale.dto.ImpactResponse;
import com.pi.frioVale.dto.ImpactSummaryResponse;
import com.pi.frioVale.dto.ThingSpeakSyncResponse;
import com.pi.frioVale.exception.BusinessRuleException;
import com.pi.frioVale.service.ImpactService;
import com.pi.frioVale.service.ThingSpeakLogService;
import com.pi.frioVale.service.TripService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
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
        return impactService.getByContainer(containerId);
    }

    /** Impactos mais recentes de todos os contêineres (Logs IoT). */
    @GetMapping
    public List<ImpactResponse> recent() {
        return impactService.getRecent();
    }

    /** Alertas de impacto recentes (tela de Alertas). */
    @GetMapping("/alerts")
    public List<ImpactAlertNotificationDTO> recentAlerts() {
        return impactService.getRecentAlerts();
    }

    /** Força uma varredura manual dos impactos no canal do contêiner. */
    @PostMapping("/sync")
    public ThingSpeakSyncResponse sync(
            @RequestParam Long containerId,
            @RequestParam(defaultValue = "100") int results) {
        // Reaproveita a sincronização de logs (que já processa impactos por contêiner),
        // garantindo que os logs brutos e os impactos sejam gravados no mesmo fluxo.
        int saved = thingSpeakLogService.sync(containerId, Math.max(1, results));
        return new ThingSpeakSyncResponse(saved);
    }

    /** Mesmo padrão de checagem de acesso do StompAuthChannelInterceptor. */
    private void assertTripAccess(Long tripId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean company = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_COMPANY".equals(a.getAuthority()));
        Long userId = null;
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            try {
                userId = Long.valueOf(jwt.getSubject());
            } catch (NumberFormatException ignored) {
                // sem usuário identificável: negado abaixo
            }
        }
        if (userId == null || !tripService.canAccessTrip(tripId, userId, company)) {
            throw new BusinessRuleException("Sem acesso a esta viagem", HttpStatus.FORBIDDEN);
        }
    }
}
