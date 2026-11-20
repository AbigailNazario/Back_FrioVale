package com.pi.frioVale.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.pi.frioVale.domain.enums.TripStatus;
import com.pi.frioVale.dto.DriverStatsResponse;
import com.pi.frioVale.dto.ImpactResponse;
import com.pi.frioVale.dto.ImpactSummaryResponse;
import com.pi.frioVale.dto.TelemetryResponse;
import com.pi.frioVale.dto.TripMonitorResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Gera os PDFs dos relatórios: sintese e detalhe por viagem. */
@Service
@RequiredArgsConstructor
public class ReportPdfService {

    private static final DateTimeFormatter GENERATED_AT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final Color PRIMARY = new Color(19, 74, 102);
    private static final Color LIGHT = new Color(235, 242, 246);
    private static final Color ALERT_RED = new Color(192, 57, 43);

    private final TripService tripService;
    private final ReportService reportService;
    private final TelemetryService telemetryService;
    private final ImpactService impactService;
    private final Clock clock;

    /** Relatório sintese (viagens ou motoristas selecionados). */
    @Transactional(readOnly = true)
    public byte[] generateSummary(String mode, int month, int year, List<Long> ids) {
        boolean driverMode = "driver".equalsIgnoreCase(mode);
        List<Long> selected = ids == null ? List.of() : ids;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 36, 36);
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            document.add(new Paragraph("Relatório FrioVale", titleFont()));
            document.add(new Paragraph("Monitoramento de cadeia fria para transporte de frutas", subtitleFont()));
            document.add(new Paragraph("Período: " + monthName(month) + " de " + year, bodyFont()));
            document.add(new Paragraph(
                    "Gerado em " + LocalDateTime.now(clock).format(GENERATED_AT), smallFont()));
            document.add(new Paragraph(" "));

            if (driverMode) {
                addDrivers(document, selected);
            } else {
                addTrips(document, selected);
            }

            document.close();
            return out.toByteArray();
        } catch (DocumentException ex) {
            throw new IllegalStateException("Não foi possível gerar o PDF do relatório", ex);
        }
    }

    /** Relatório detalhado de UMA viagem: todas as leituras de temperatura e umidade com horário. */
    @Transactional(readOnly = true)
    public byte[] generateTripDetail(Long tripId) {
        List<TripMonitorResponse> trips = tripService.findAllMonitor();
        TripMonitorResponse trip = trips.stream()
                .filter(t -> t.id().equals(tripId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Viagem não encontrada: " + tripId));

        List<TelemetryResponse> readings = telemetryService.getHistory(tripId);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 36, 36);
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            document.add(new Paragraph("Relatório Detalhado - Viagem " + tripCode(tripId), titleFont()));
            document.add(new Paragraph("Cadeia Fria - FrioVale", subtitleFont()));
            document.add(new Paragraph("Gerado em " + LocalDateTime.now(clock).format(GENERATED_AT), smallFont()));
            document.add(new Paragraph(" "));

            // Cabeçalho da viagem
            PdfPTable info = new PdfPTable(new float[]{2f, 5f});
            info.setWidthPercentage(100);
            addInfoRow(info, "Viagem", tripCode(tripId));
            addInfoRow(info, "Contêiner", trip.containerCode());
            addInfoRow(info, "Motorista", trip.driverName() != null ? trip.driverName() : "—");
            addInfoRow(info, "Fruta", trip.fruitName());
            addInfoRow(info, "Faixa de temperatura", range(trip.minTemp(), trip.maxTemp()));
            addInfoRow(info, "Faixa de umidade", range(trip.minHumidity(), trip.maxHumidity()));
            addInfoRow(info, "Rota", route(trip));
            addInfoRow(info, "Status", statusLabel(trip.status()));
            addInfoRow(info, "Início", datetime(trip.startDate()));
            addInfoRow(info, "Término", datetime(trip.endDate()));
            document.add(info);
            document.add(new Paragraph(" "));

            // Resumo
            document.add(sectionTitle("Resumo"));
            long inRange = readings.stream().filter(r -> r.isAlert() == Boolean.FALSE).count();
            long outOfRange = readings.size() - inRange;
            Double minTemp = readings.stream().map(TelemetryResponse::temperature)
                    .filter(java.util.Objects::nonNull).min(Double::compare).orElse(null);
            Double maxTemp = readings.stream().map(TelemetryResponse::temperature)
                    .filter(java.util.Objects::nonNull).max(Double::compare).orElse(null);
            Double minHum = readings.stream().map(TelemetryResponse::humidity)
                    .filter(java.util.Objects::nonNull).min(Double::compare).orElse(null);
            Double maxHum = readings.stream().map(TelemetryResponse::humidity)
                    .filter(java.util.Objects::nonNull).max(Double::compare).orElse(null);

            PdfPTable summary = new PdfPTable(new float[]{2.5f, 2.5f, 2.5f, 2.5f});
            summary.setWidthPercentage(100);
            addHeader(summary, "Total de leituras");
            addHeader(summary, "Dentro da faixa");
            addHeader(summary, "Fora da faixa");
            addHeader(summary, "Percentual na faixa");
            addCell(summary, String.valueOf(readings.size()), 0);
            addCell(summary, String.valueOf(inRange), 1);
            addCell(summary, String.valueOf(outOfRange), 2);
            addCell(summary, readings.isEmpty() ? "—" : Math.round(inRange * 100.0 / readings.size()) + "%", 3);
            document.add(summary);
            document.add(new Paragraph(" "));

            if (minTemp != null) {
                PdfPTable extremes = new PdfPTable(new float[]{2f, 2f, 2f, 2f});
                extremes.setWidthPercentage(100);
                addHeader(extremes, "Temperatura mínima");
                addHeader(extremes, "Temperatura máxima");
                addHeader(extremes, "Umidade mínima");
                addHeader(extremes, "Umidade máxima");
                addCell(extremes, temperature(minTemp), 0);
                addCell(extremes, temperature(maxTemp), 1);
                addCell(extremes, percent(minHum), 2);
                addCell(extremes, percent(maxHum), 3);
                document.add(extremes);
                document.add(new Paragraph(" "));
            }

            // Impactos do acelerômetro (mesmos valores da aba Impactos)
            addImpactsSection(document, tripId);

            // Tabela detalhada de leituras
            document.add(sectionTitle("Leituras ao longo do trajeto (" + readings.size() + ")"));
            if (readings.isEmpty()) {
                document.add(new Paragraph("Nenhuma leitura registrada para esta viagem.", bodyFont()));
            } else {
                PdfPTable table = new PdfPTable(new float[]{2.2f, 2.2f, 2.2f, 2.2f, 1.6f});
                table.setWidthPercentage(100);
                addHeader(table, "Data / hora");
                addHeader(table, "Temperatura");
                addHeader(table, "Umidade");
                addHeader(table, "Status");
                addHeader(table, "Alerta");

                int row = 0;
                for (TelemetryResponse r : readings) {
                    addCell(table, timestamp(r.timestamp()), row);
                    addCell(table, temperature(r.temperature()), row);
                    addCell(table, percent(r.humidity()), row);
                    addCell(table, r.isAlert() == Boolean.TRUE ? "FORA DA FAIXA" : "Dentro", row);
                    addCell(table, r.isAlert() == Boolean.TRUE ? "SIM" : "não", row);
                    row++;
                }
                document.add(table);
            }

            document.close();
            return out.toByteArray();
        } catch (DocumentException ex) {
            throw new IllegalStateException("Não foi possível gerar o PDF detalhado da viagem", ex);
        }
    }

    /** Seção de impactos do acelerômetro: resumo + tabela de picos por ocorrência. */
    private void addImpactsSection(Document document, Long tripId) throws DocumentException {
        ImpactSummaryResponse summary = impactService.getSummary(tripId);
        document.add(sectionTitle("Impactos na viagem"));

        PdfPTable impactsSummary = new PdfPTable(new float[]{2.5f, 2.5f, 5f});
        impactsSummary.setWidthPercentage(100);
        addHeader(impactsSummary, "Total de impactos");
        addHeader(impactsSummary, "Maior pico");
        addHeader(impactsSummary, "Último impacto");
        addCell(impactsSummary, String.valueOf(summary.total()), 0);
        addCell(impactsSummary, peak(summary.maxPeakG()), 0);
        addCell(impactsSummary, summary.lastImpact() == null
                ? "—" : timestamp(summary.lastImpact().originalTimestamp()), 0);
        document.add(impactsSummary);
        document.add(new Paragraph(" "));

        List<ImpactResponse> impacts = impactService.getByTrip(tripId);
        if (summary.total() == 0) {
            String message = switch (summary.sensorStatus() == null ? "" : summary.sensorStatus()) {
                case "NO_RECENT_DATA" -> "Sem dados recentes do acelerômetro";
                case "CALIBRATING" -> "Sensor em calibração";
                default -> "Nenhum impacto registrado";
            };
            document.add(new Paragraph(message, bodyFont()));
        } else {
            PdfPTable table = new PdfPTable(new float[]{5f, 3f});
            table.setWidthPercentage(100);
            addHeader(table, "Data / hora");
            addHeader(table, "Pico (g)");
            int row = 0;
            for (ImpactResponse impact : impacts) {
                addCell(table, timestamp(impact.originalTimestamp()), row);
                addCell(table, peak(impact.peakG()), row);
                row++;
            }
            document.add(table);
        }
        document.add(new Paragraph(" "));
    }

    private static String peak(Double value) {
        return value == null ? "—" : String.format(PT_BR, "%.1f g", value);
    }

    /* ---------- sintese (mantida) ---------- */

    private void addTrips(Document document, List<Long> ids) throws DocumentException {
        List<TripMonitorResponse> trips = tripService.findAllMonitor();
        if (!ids.isEmpty()) {
            trips = trips.stream().filter(trip -> ids.contains(trip.id())).toList();
        }

        document.add(sectionTitle("Desempenho por viagem"));
        document.add(new Paragraph("Registros selecionados: " + trips.size(), smallFont()));
        document.add(new Paragraph(" "));

        if (trips.isEmpty()) {
            document.add(new Paragraph("Nenhuma viagem selecionada.", bodyFont()));
            return;
        }

        PdfPTable table = new PdfPTable(new float[]{1.8f, 3.2f, 1.8f, 1.8f, 1.6f, 1.6f});
        table.setWidthPercentage(100);
        addHeader(table, "Viagem");
        addHeader(table, "Rota / contêiner");
        addHeader(table, "Fruta");
        addHeader(table, "Status");
        addHeader(table, "Última temp.");
        addHeader(table, "Na faixa");

        int row = 0;
        for (TripMonitorResponse trip : trips) {
            addCell(table, tripCode(trip.id()), row);
            addCell(table, route(trip), row);
            addCell(table, trip.fruitName(), row);
            addCell(table, statusLabel(trip.status()), row);
            addCell(table, temperature(trip.temperature()), row);
            addCell(table, percent(trip.inRangePercent()), row);
            row++;
        }
        document.add(table);
    }

    private void addDrivers(Document document, List<Long> ids) throws DocumentException {
        List<DriverStatsResponse> drivers = reportService.driverStats();
        if (!ids.isEmpty()) {
            drivers = drivers.stream().filter(driver -> ids.contains(driver.id())).toList();
        }

        document.add(sectionTitle("Desempenho por motorista"));
        document.add(new Paragraph("Registros selecionados: " + drivers.size(), smallFont()));
        document.add(new Paragraph(" "));

        if (drivers.isEmpty()) {
            document.add(new Paragraph("Nenhum motorista selecionado.", bodyFont()));
            return;
        }

        PdfPTable table = new PdfPTable(new float[]{3f, 1.6f, 2f});
        table.setWidthPercentage(100);
        addHeader(table, "Motorista");
        addHeader(table, "Viagens");
        addHeader(table, "Leituras na faixa");

        int row = 0;
        for (DriverStatsResponse driver : drivers) {
            addCell(table, driver.name(), row);
            addCell(table, String.valueOf(driver.trips()), row);
            addCell(table, percent(driver.inRangePercent()), row);
            row++;
        }
        document.add(table);
    }

    private static void addInfoRow(PdfPTable table, String label, String value) {
        PdfPCell k = new PdfPCell(new Phrase(label, boldFont()));
        k.setBackgroundColor(LIGHT);
        k.setPadding(5);
        table.addCell(k);
        PdfPCell v = new PdfPCell(new Phrase(value == null || value.isBlank() ? "—" : value, bodyFont()));
        v.setPadding(5);
        table.addCell(v);
    }

    private static void addHeader(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, headerFont()));
        cell.setBackgroundColor(PRIMARY);
        cell.setPadding(6);
        table.addCell(cell);
    }

    private static void addCell(PdfPTable table, String text, int row) {
        PdfPCell cell = new PdfPCell(new Phrase(text == null || text.isBlank() ? "—" : text, bodyFont()));
        cell.setPadding(5);
        if (row % 2 == 1) {
            cell.setBackgroundColor(LIGHT);
        }
        table.addCell(cell);
    }

    private static String tripCode(Long id) {
        return "FV-" + String.format("%03d", id);
    }

    private static String route(TripMonitorResponse trip) {
        if (trip.origin() != null && trip.destination() != null) {
            return trip.origin() + " → " + trip.destination();
        }
        return trip.containerCode() + " • " + trip.fruitName();
    }

    private static String statusLabel(TripStatus status) {
        if (status == null) {
            return "—";
        }
        return switch (status) {
            case PLANNED -> "Planejada";
            case IN_TRANSIT -> "Em andamento";
            case COMPLETED -> "Concluída";
        };
    }

    private static String temperature(Double value) {
        return value == null ? "—" : String.format(PT_BR, "%.1f °C", value);
    }

    private static String percent(Double value) {
        return value == null ? "—" : String.format(PT_BR, "%.0f%%", value);
    }

    private static String percent(Integer value) {
        return value == null ? "—" : value + "%";
    }

    private static String range(Double min, Double max) {
        if (min == null && max == null) {
            return "—";
        }
        return temperature(min) + " a " + temperature(max);
    }

    private static String datetime(LocalDateTime value) {
        return value == null ? "—" : value.format(TIMESTAMP);
    }

    private static String timestamp(LocalDateTime value) {
        return value == null ? "—" : value.format(TIMESTAMP);
    }

    private static String monthName(int month) {
        String[] names = {
                "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
                "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"
        };
        return month >= 1 && month <= 12 ? names[month - 1] : String.valueOf(month);
    }

    private static Font titleFont() {
        return new Font(Font.HELVETICA, 20, Font.BOLD, PRIMARY);
    }

    private static Font subtitleFont() {
        return new Font(Font.HELVETICA, 11, Font.NORMAL, Color.DARK_GRAY);
    }

    private static Font sectionTitleFont() {
        return new Font(Font.HELVETICA, 14, Font.BOLD, PRIMARY);
    }

    private static Font headerFont() {
        return new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE);
    }

    private static Font boldFont() {
        return new Font(Font.HELVETICA, 9, Font.BOLD, Color.BLACK);
    }

    private static Font bodyFont() {
        return new Font(Font.HELVETICA, 9, Font.NORMAL, Color.BLACK);
    }

    private static Font smallFont() {
        return new Font(Font.HELVETICA, 8, Font.NORMAL, Color.GRAY);
    }

    private static Paragraph sectionTitle(String text) {
        Paragraph paragraph = new Paragraph(text, sectionTitleFont());
        paragraph.setSpacingAfter(6);
        return paragraph;
    }
}
