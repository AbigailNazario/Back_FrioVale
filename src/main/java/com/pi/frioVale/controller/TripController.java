package com.pi.frioVale.controller;

import com.pi.frioVale.dto.AlertResponse;
import com.pi.frioVale.dto.TelemetryResponse;
import com.pi.frioVale.dto.TripRequest;
import com.pi.frioVale.dto.TripResponse;
import com.pi.frioVale.service.TelemetryService;
import com.pi.frioVale.service.TripService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;
    private final TelemetryService telemetryService;

    /** Cadastra a viagem e (por padrão) já a inicia. */
    @PostMapping
    public ResponseEntity<TripResponse> create(@Valid @RequestBody TripRequest request) {
        TripResponse created = tripService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/active")
    public List<TripResponse> listActive() {
        return tripService.findActive();
    }

    @GetMapping("/{id}/telemetry")
    public List<TelemetryResponse> telemetry(@PathVariable Long id) {
        return telemetryService.getHistory(id);
    }

    @GetMapping("/{id}/alerts")
    public List<AlertResponse> alerts(@PathVariable Long id) {
        return telemetryService.getAlerts(id);
    }

    @PostMapping("/{id}/start")
    public TripResponse start(@PathVariable Long id) {
        return tripService.startTrip(id);
    }

    @PostMapping("/{id}/end")
    public TripResponse end(@PathVariable Long id) {
        return tripService.endTrip(id);
    }
}
