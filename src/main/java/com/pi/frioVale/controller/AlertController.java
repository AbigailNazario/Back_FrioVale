package com.pi.frioVale.controller;

import com.pi.frioVale.security.AccessGuard;
import com.pi.frioVale.dto.AlertResponse;
import com.pi.frioVale.service.TelemetryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final TelemetryService telemetryService;
    private final AccessGuard accessGuard; // [Seguranca - IDOR]

    /** Todos os alertas, do mais recente para o mais antigo. */
    @GetMapping
    public List<AlertResponse> list() {
        return accessGuard.onlyOwnTrips(telemetryService.getAllAlerts(), AlertResponse::tripId); // [Seguranca - IDOR]
    }
}
