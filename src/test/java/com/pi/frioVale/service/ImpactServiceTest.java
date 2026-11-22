package com.pi.frioVale.service;

import com.pi.frioVale.config.ImpactProperties;
import com.pi.frioVale.domain.Container;
import com.pi.frioVale.domain.ImpactAlert;
import com.pi.frioVale.domain.ImpactEvent;
import com.pi.frioVale.domain.ThingSpeakLog;
import com.pi.frioVale.domain.Trip;
import com.pi.frioVale.domain.enums.TripStatus;
import com.pi.frioVale.dto.ImpactSummaryResponse;
import com.pi.frioVale.event.ImpactAlertCreatedEvent;
import com.pi.frioVale.integration.thingspeak.ThingSpeakReading;
import com.pi.frioVale.repository.ImpactAlertRepository;
import com.pi.frioVale.repository.ImpactEventRepository;
import com.pi.frioVale.repository.ThingSpeakLogRepository;
import com.pi.frioVale.repository.TripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Testes de lógica de negócio do monitoramento de impactos do acelerômetro.
 * Estilo semelhante ao IaPredictionServiceTest: Mockito puro, Clock fixo.
 */
@ExtendWith(MockitoExtension.class)
class ImpactServiceTest {

    private static final String CHANNEL = "123456";
    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 11, 20, 12, 0, 0);

    @Mock
    private ImpactEventRepository impactRepository;
    @Mock
    private ImpactAlertRepository impactAlertRepository;
    @Mock
    private TripRepository tripRepository;
    @Mock
    private ThingSpeakLogRepository thingSpeakLogRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private Clock clock;
    private ImpactService service;

    private Container container;
    private Trip trip;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-11-20T12:00:00Z"), ZoneOffset.UTC);
        ImpactProperties properties = new ImpactProperties(true, 2.0, 2L);
        service = new ImpactService(
                impactRepository, impactAlertRepository, tripRepository,
                thingSpeakLogRepository, eventPublisher, clock, properties);

        container = new Container();
        container.setId(1L);
        container.setCode("CONT-102");
        container.setThingSpeakChannelId(CHANNEL);

        trip = new Trip();
        trip.setId(10L);
        trip.setContainer(container);
        trip.setStatus(TripStatus.IN_TRANSIT);
        trip.setStartDate(NOW.minusHours(1));
    }

    private ImpactService serviceWith(boolean alertEnabled) {
        return new ImpactService(
                impactRepository, impactAlertRepository, tripRepository,
                thingSpeakLogRepository, eventPublisher, clock,
                new ImpactProperties(alertEnabled, 2.0, 2L));
    }

    private ThingSpeakReading impact(Long entryId, LocalDateTime ts, Double peak, String plate) {
        return new ThingSpeakReading(entryId, ts, 800, peak, plate);
    }

    @Test
    @DisplayName("Evento 800 válido com viagem ativa salva ImpactEvent + ImpactAlert")
    void eventoValidoComViagemAtivaSalvaImpactoEAlerta() {
        LocalDateTime moment = NOW.minusMinutes(5);
        when(tripRepository.findActiveAt(eq(1L), eq(moment), any(Pageable.class)))
                .thenReturn(List.of(trip));
        when(impactRepository.save(any(ImpactEvent.class))).thenAnswer(inv -> {
            ImpactEvent e = inv.getArgument(0);
            e.setId(100L);
            return e;
        });
        when(impactAlertRepository.save(any(ImpactAlert.class))).thenAnswer(inv -> {
            ImpactAlert a = inv.getArgument(0);
            a.setId(200L);
            return a;
        });

        int saved = service.processReadings(container,
                List.of(impact(500L, moment, 3.4, "ABC-1234")));

        assertThat(saved).isEqualTo(1);
        ArgumentCaptor<ImpactEvent> eventCaptor = ArgumentCaptor.forClass(ImpactEvent.class);
        verify(impactRepository).save(eventCaptor.capture());
        ImpactEvent event = eventCaptor.getValue();
        assertThat(event.getTrip().getId()).isEqualTo(10L);
        assertThat(event.getPeakG()).isEqualTo(3.4);
        assertThat(event.getPlate()).isEqualTo("ABC-1234");
        assertThat(event.getOriginalTimestamp()).isEqualTo(moment);
        assertThat(event.getReceivedAt()).isEqualTo(NOW);

        verify(impactAlertRepository).save(any(ImpactAlert.class));
        verify(eventPublisher).publishEvent(any(ImpactAlertCreatedEvent.class));
    }

    @Test
    @DisplayName("Reprocessamento do mesmo entryId não duplica impacto nem alerta")
    void reprocessamentoNaoDuplicaImpactoNemAlerta() {
        LocalDateTime moment = NOW.minusMinutes(5);
        when(impactRepository.existsByChannelIdAndEntryId(CHANNEL, 500L)).thenReturn(true);

        int saved = service.processReadings(container,
                List.of(impact(500L, moment, 3.4, "ABC-1234")));

        assertThat(saved).isZero();
        verify(impactRepository, never()).save(any());
        verify(impactAlertRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("Evento recebido com atraso é associado à viagem correta pelo intervalo")
    void eventoAtrasadoAssociadoAViagemCorreta() {
        LocalDateTime oldMoment = NOW.minusMinutes(30); // dentro da viagem (iniciou há 1h)
        when(tripRepository.findActiveAt(eq(1L), eq(oldMoment), any(Pageable.class)))
                .thenReturn(List.of(trip));
        when(impactRepository.save(any(ImpactEvent.class))).thenAnswer(inv -> {
            ImpactEvent e = inv.getArgument(0);
            e.setId(101L);
            return e;
        });
        when(impactAlertRepository.save(any(ImpactAlert.class))).thenAnswer(inv -> {
            ImpactAlert a = inv.getArgument(0);
            a.setId(201L);
            return a;
        });

        service.processReadings(container, List.of(impact(501L, oldMoment, 2.5, "XYZ-9999")));

        ArgumentCaptor<ImpactEvent> captor = ArgumentCaptor.forClass(ImpactEvent.class);
        verify(impactRepository).save(captor.capture());
        assertThat(captor.getValue().getTrip().getId()).isEqualTo(10L);
        assertThat(captor.getValue().getOriginalTimestamp()).isEqualTo(oldMoment);
        assertThat(captor.getValue().getReceivedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("Pico inválido (null/negativo/acima de 100) é descartado")
    void picoInvalidoDescartado() {
        LocalDateTime moment = NOW.minusMinutes(5);
        int saved = service.processReadings(container, List.of(
                impact(502L, moment, null, "P1"),
                impact(503L, moment, -1.0, "P2"),
                impact(504L, moment, 150.0, "P3")));

        assertThat(saved).isZero();
        verify(impactRepository, never()).save(any());
        verify(impactAlertRepository, never()).save(any());
    }

    @Test
    @DisplayName("Evento sem viagem ativa é salvo com trip nula")
    void eventoSemViagemSalvoComTripNula() {
        LocalDateTime moment = NOW.minusMinutes(5);
        when(tripRepository.findActiveAt(eq(1L), eq(moment), any(Pageable.class)))
                .thenReturn(List.of());
        when(impactRepository.save(any(ImpactEvent.class))).thenAnswer(inv -> {
            ImpactEvent e = inv.getArgument(0);
            e.setId(102L);
            return e;
        });

        int saved = service.processReadings(container, List.of(impact(505L, moment, 1.0, "SEM-VIAGEM")));

        assertThat(saved).isEqualTo(1);
        ArgumentCaptor<ImpactEvent> captor = ArgumentCaptor.forClass(ImpactEvent.class);
        verify(impactRepository).save(captor.capture());
        assertThat(captor.getValue().getTrip()).isNull();
        // 1.0 g < 2.0 (threshold) => sem alerta
        verify(impactAlertRepository, never()).save(any());
    }

    @Test
    @DisplayName("Summary: sem impactos com sensor ativo retorna ACTIVE_NO_IMPACTS")
    void summarySemImpactosSensorAtivo() {
        when(thingSpeakLogRepository.findFirstByContainerIdOrderByRecordedAtDesc(1L))
                .thenReturn(Optional.of(logAt(NOW.minusSeconds(30))));
        when(tripRepository.findDetailedById(10L)).thenReturn(Optional.of(trip));
        when(impactRepository.countByTripId(10L)).thenReturn(0L);
        when(impactRepository.findFirstByTripIdOrderByOriginalTimestampDesc(10L))
                .thenReturn(Optional.empty());

        ImpactSummaryResponse summary = service.getSummary(10L);

        assertThat(summary.total()).isZero();
        assertThat(summary.sensorStatus()).isEqualTo("ACTIVE_NO_IMPACTS");
        assertThat(summary.message()).isEqualTo("Nenhum impacto registrado");
    }

    @Test
    @DisplayName("Summary: dispositivo sem dados recentes retorna NO_RECENT_DATA")
    void summarySemDadosRecentes() {
        when(thingSpeakLogRepository.findFirstByContainerIdOrderByRecordedAtDesc(1L))
                .thenReturn(Optional.of(logAt(NOW.minusMinutes(10))));
        when(tripRepository.findDetailedById(10L)).thenReturn(Optional.of(trip));
        when(impactRepository.countByTripId(10L)).thenReturn(0L);
        when(impactRepository.findFirstByTripIdOrderByOriginalTimestampDesc(10L))
                .thenReturn(Optional.empty());

        ImpactSummaryResponse summary = service.getSummary(10L);

        assertThat(summary.sensorStatus()).isEqualTo("NO_RECENT_DATA");
        assertThat(summary.message()).isEqualTo("Sem dados recentes");
    }

    @Test
    @DisplayName("Alerta desabilitado via properties não cria ImpactAlert")
    void alertaDesabilitadoNaoCriaAlerta() {
        LocalDateTime moment = NOW.minusMinutes(5);
        when(tripRepository.findActiveAt(eq(1L), eq(moment), any(Pageable.class)))
                .thenReturn(List.of(trip));
        when(impactRepository.save(any(ImpactEvent.class))).thenAnswer(inv -> {
            ImpactEvent e = inv.getArgument(0);
            e.setId(103L);
            return e;
        });

        int saved = serviceWith(false).processReadings(container,
                List.of(impact(506L, moment, 5.0, "ALERTA-OFF")));

        assertThat(saved).isEqualTo(1);
        verify(impactAlertRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    private ThingSpeakLog logAt(LocalDateTime recordedAt) {
        return ThingSpeakLog.builder()
                .container(container)
                .channelId(CHANNEL)
                .entryId(999L)
                .recordedAt(recordedAt)
                .fetchedAt(recordedAt)
                .build();
    }
}
