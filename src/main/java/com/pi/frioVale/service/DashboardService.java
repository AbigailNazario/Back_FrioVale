package com.pi.frioVale.service;

import com.pi.frioVale.dto.AlertResponse;
import com.pi.frioVale.dto.DashboardSummaryResponse;
import com.pi.frioVale.dto.TripMonitorResponse;
import com.pi.frioVale.repository.AlertRepository;
import com.pi.frioVale.repository.ImpactAlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final TripService tripService;
    private final TelemetryService telemetryService;
    private final AlertRepository alertRepository;
    private final ImpactAlertRepository impactAlertRepository;

    @Transactional(readOnly = true)
    public DashboardSummaryResponse summary() {
        List<TripMonitorResponse> trips = tripService.findActiveMonitor();
        List<AlertResponse> recentAlerts = telemetryService.getRecentAlerts(5);

        long tripsInRange = trips.stream()
                .filter(trip -> Boolean.TRUE.equals(trip.inRange()))
                .count();
        long activeDrivers = trips.stream()
                .map(TripMonitorResponse::driverId)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        Double idealMinTemp = trips.stream()
                .map(TripMonitorResponse::minTemp)
                .filter(Objects::nonNull)
                .min(Double::compareTo)
                .orElse(null);
        Double idealMaxTemp = trips.stream()
                .map(TripMonitorResponse::maxTemp)
                .filter(Objects::nonNull)
                .max(Double::compareTo)
                .orElse(null);
        // Inclui os alertas de impacto do acelerômetro no contador do dashboard.
        // Os alertas térmicos permanecem em Alert; os de impacto ficam em ImpactAlert.
        long activeAlerts = alertRepository.countByResolvedFalse()
                + impactAlertRepository.countByResolvedFalse();

        return new DashboardSummaryResponse(
                trips.size(),
                tripsInRange,
                idealMinTemp,
                idealMaxTemp,
                activeAlerts,
                activeDrivers,
                trips,
                recentAlerts);
    }
}
