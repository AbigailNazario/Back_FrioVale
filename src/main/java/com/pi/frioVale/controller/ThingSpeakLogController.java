package com.pi.frioVale.controller;

import com.pi.frioVale.security.AccessGuard;
import com.pi.frioVale.dto.ThingSpeakLogResponse;
import com.pi.frioVale.dto.ThingSpeakSyncResponse;
import com.pi.frioVale.service.ThingSpeakLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Tela de logs do IoT: consulta os registros já gravados e permite forçar uma nova coleta. */
@RestController
@RequestMapping("/api/thingspeak/logs")
@RequiredArgsConstructor
public class ThingSpeakLogController {

    private final ThingSpeakLogService thingSpeakLogService;
    private final AccessGuard accessGuard; // [Seguranca - IDOR]

    @GetMapping
    public List<ThingSpeakLogResponse> list(@RequestParam(required = false) Long containerId) {
        if (containerId != null) {
            accessGuard.requireContainer(containerId); // [Seguranca - IDOR] leituras brutas do dispositivo
        }
        return accessGuard.onlyOwnContainers(thingSpeakLogService.list(containerId), ThingSpeakLogResponse::containerId);
    }

    /** Consulta os canais novamente e persiste as novas entradas. */
    @PostMapping("/sync")
    public ThingSpeakSyncResponse sync(
            @RequestParam(required = false) Long containerId,
            @RequestParam(defaultValue = "100") int results) {
        // [Seguranca - IDOR] comando: sincronizar um dispositivo exige ser dono; sincronizar todos, ser gestor
        if (containerId == null) {
            accessGuard.requireCompany("thingspeak:sync-all");
        } else {
            accessGuard.requireContainer(containerId);
        }
        return new ThingSpeakSyncResponse(thingSpeakLogService.sync(containerId, results));
    }
}
