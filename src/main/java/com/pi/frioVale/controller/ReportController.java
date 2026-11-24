package com.pi.frioVale.controller;

import com.pi.frioVale.security.AccessGuard;
import com.pi.frioVale.dto.DriverStatsResponse;
import com.pi.frioVale.service.ReportPdfService;
import com.pi.frioVale.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final ReportPdfService reportPdfService;
    private final AccessGuard accessGuard; // [Seguranca - IDOR]

    @GetMapping("/drivers")
    public List<DriverStatsResponse> drivers() {
        accessGuard.requireCompany("report:drivers"); // [Seguranca - IDOR] dados de todos os motoristas
        return reportService.driverStats();
    }

    /** Gera o PDF de sintese para as viagens/motoristas selecionados na tela. */
    @GetMapping("/pdf")
    public ResponseEntity<byte[]> pdf(
            @RequestParam(defaultValue = "trip") String mode,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) List<Long> ids) {
        accessGuard.requireCompany("report:summary"); // [Seguranca - IDOR] recebe IDs de viagens/motoristas de qualquer um

        LocalDate today = LocalDate.now();
        int reportMonth = month == null ? today.getMonthValue() : month;
        int reportYear = year == null ? today.getYear() : year;

        byte[] content = reportPdfService.generateSummary(mode, reportMonth, reportYear, ids);
        String filename = "relatorio-friovale-%d-%02d.pdf".formatted(reportYear, reportMonth);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(content);
    }

    /** Gera o PDF detalhado de UMA viagem: todas as leituras de temperatura e umidade com horario. */
    @GetMapping("/trips/{tripId}/pdf")
    public ResponseEntity<byte[]> tripPdf(@PathVariable Long tripId) {
        accessGuard.requireTrip(tripId); // [Seguranca - IDOR] todas as leituras da viagem
        byte[] content = reportPdfService.generateTripDetail(tripId);
        String filename = "relatorio-viagem-fv-%03d.pdf".formatted(tripId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(content);
    }
}
