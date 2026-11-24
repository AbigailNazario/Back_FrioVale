package com.pi.frioVale.controller;

import com.pi.frioVale.security.AccessGuard;
import com.pi.frioVale.dto.AlertResponse;
import com.pi.frioVale.dto.TelemetryResponse;
import com.pi.frioVale.dto.TripMonitorResponse;
import com.pi.frioVale.dto.TripRequest;
import com.pi.frioVale.dto.TripResponse;
import com.pi.frioVale.dto.TripUpdateRequest;
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
    private final AccessGuard accessGuard; // [Seguranca - IDOR]

    /** Cadastra a viagem e (por padrão) já a inicia. */
    @PostMapping
    public ResponseEntity<TripResponse> create(@Valid @RequestBody TripRequest request) {
        // [Seguranca - IDOR] so o gestor atribui conteiner/motorista; senao o motorista viraria "dono" do dispositivo de outro
        accessGuard.requireCompany("trip:create");
        TripResponse created = tripService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/active")
    public List<TripResponse> listActive() {
        return accessGuard.onlyOwnTrips(tripService.findActive(), TripResponse::id); // [Seguranca - IDOR]
    }

    /** Lista todas as viagens com última leitura e indicadores (dados vindos do banco). */
    @GetMapping
    public List<TripMonitorResponse> list() {
        return accessGuard.onlyOwnTrips(tripService.findAllMonitor(), TripMonitorResponse::id); // [Seguranca - IDOR]
    }

    @GetMapping("/active/monitor")
    public List<TripMonitorResponse> listActiveMonitor() {
        return accessGuard.onlyOwnTrips(tripService.findActiveMonitor(), TripMonitorResponse::id); // [Seguranca - IDOR]
    }

    @GetMapping("/{id}/telemetry")
    public List<TelemetryResponse> telemetry(@PathVariable Long id) {
        accessGuard.requireTrip(id); // [Seguranca - IDOR] leituras do dispositivo
        return telemetryService.getHistory(id);
    }

    /** Preenche os campos do acelerômetro em leituras antigas (a partir dos logs já importados). */
    @PostMapping("/{id}/backfill-accelerometer")
    public java.util.Map<String, Object> backfillAccelerometer(@PathVariable Long id) {
        accessGuard.requireTrip(id); // [Seguranca - IDOR] comando
        int atualizadas = telemetryService.backfillAccelerometerFields(id);
        return java.util.Map.of("status", "ok", "tripId", id, "atualizadas", atualizadas);
    }

    @GetMapping("/{id}/alerts")
    public List<AlertResponse> alerts(@PathVariable Long id) {
        accessGuard.requireTrip(id); // [Seguranca - IDOR]
        return telemetryService.getAlerts(id);
    }

    @PostMapping("/{id}/start")
    public TripResponse start(@PathVariable Long id) {
        accessGuard.requireTrip(id); // [Seguranca - IDOR] comando
        return tripService.startTrip(id);
    }

    @PostMapping("/{id}/end")
    public TripResponse end(@PathVariable Long id) {
        accessGuard.requireTrip(id); // [Seguranca - IDOR] comando
        return tripService.endTrip(id);
    }

    /** Altera o status da viagem (PLANNED / IN_TRANSIT / COMPLETED). */
    @PutMapping("/{id}/status")
    public TripResponse updateStatus(@PathVariable Long id, @Valid @RequestBody TripUpdateRequest request) {
        accessGuard.requireTrip(id); // [Seguranca - IDOR] comando
        return tripService.updateStatus(id, request.status());
    }

    /** Atualiza campos e status da viagem. */
    @PutMapping("/{id}")
    public TripResponse updateTrip(@PathVariable Long id, @Valid @RequestBody TripUpdateRequest request) {
        accessGuard.requireTrip(id); // [Seguranca - IDOR] configuracao
        if (request.containerId() != null || request.driverId() != null) {
            // trocar conteiner ou motorista da viagem e reatribuir o dispositivo: so o gestor
            accessGuard.requireCompany("trip:" + id + ":reatribuir");
        }
        return tripService.updateTrip(id, request);
    }
}
