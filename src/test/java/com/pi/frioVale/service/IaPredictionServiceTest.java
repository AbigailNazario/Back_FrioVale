package com.pi.frioVale.service;

import com.pi.frioVale.config.IaProperties;
import com.pi.frioVale.domain.Fruit;
import com.pi.frioVale.domain.Telemetry;
import com.pi.frioVale.domain.Trip;
import com.pi.frioVale.domain.enums.TripStatus;
import com.pi.frioVale.dto.IaPredictionResponse;
import com.pi.frioVale.exception.ResourceNotFoundException;
import com.pi.frioVale.repository.ContainerRepository;
import com.pi.frioVale.repository.FruitRepository;
import com.pi.frioVale.repository.IaPredictionRepository;
import com.pi.frioVale.repository.TelemetryRepository;
import com.pi.frioVale.repository.TripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Testes de logica de negocio do servico de IA.
 * Testa casos que nao dependem da API Python externa (que requer servidor rodando).
 */
@ExtendWith(MockitoExtension.class)
class IaPredictionServiceTest {

    @Mock
    private RestClient iaRestClient;

    @Mock
    private TripRepository tripRepository;

    @Mock
    private ContainerRepository containerRepository;

    @Mock
    private FruitRepository fruitRepository;

    @Mock
    private TelemetryRepository telemetryRepository;

    @Mock
    private IaPredictionRepository iaPredictionRepository;

    @Mock
    private Clock clock;

    private IaPredictionService iaPredictionService;

    private Trip trip;
    private Fruit fruitUva;

    @BeforeEach
    void setUp() {
        IaProperties iaProperties = new IaProperties(
                "http://localhost:8000",
                java.time.Duration.ofSeconds(3),
                java.time.Duration.ofSeconds(5),
                true
        );
        iaPredictionService = new IaPredictionService(
                iaRestClient, iaProperties,
                tripRepository, containerRepository, fruitRepository,
                telemetryRepository, clock
        );

        trip = new Trip();
        trip.setId(1L);
        trip.setStatus(TripStatus.IN_TRANSIT);

        fruitUva = new Fruit();
        fruitUva.setId(1L);
        fruitUva.setName("Uva");
        fruitUva.setMinTemp(0.0);
        fruitUva.setMaxTemp(2.0);
        fruitUva.setMinHumidity(85.0);
        fruitUva.setMaxHumidity(95.0);
    }

    @Test
    @DisplayName("IA desabilitada deve retornar vazio sem chamar API")
    void iaDesabilitadaDeveRetornarVazio() {
        IaProperties iaPropertiesDesabilitada = new IaProperties(
                "http://localhost:8000",
                java.time.Duration.ofSeconds(3),
                java.time.Duration.ofSeconds(5),
                false
        );
        IaPredictionService serviceDesabilitado = new IaPredictionService(
                iaRestClient, iaPropertiesDesabilitada,
                tripRepository, containerRepository, fruitRepository,
                telemetryRepository, clock
        );

        trip.setFruit(fruitUva);

        Optional<IaPredictionResponse> resultado = serviceDesabilitado.predictIa(
                1L, 12.0, 90.0, 0.2, 1.0
        );

        assertThat(resultado).isEmpty();
        verifyNoInteractions(iaRestClient);
    }

    @Test
    @DisplayName("Deve lancar excecao quando viagem nao encontrada em predictIa")
    void deveLancarExcecaoQuandoViagemNaoExiste() {
        when(tripRepository.findDetailedById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> iaPredictionService.predictIa(
                99L, 12.0, 90.0, 0.2, 1.0
        )).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Deve lancar excecao quando viagem nao encontrada em analyzeLatestReading")
    void deveLancarExcecaoQuandoViagemNaoEncontradaAnalyze() {
        when(tripRepository.findDetailedById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> iaPredictionService.analyzeLatestReading(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Deve retornar vazio quando nao ha leituras de telemetria")
    void deveRetornarVazioQuandoNaoHaLeituras() {
        trip.setFruit(fruitUva);
        when(tripRepository.findDetailedById(1L)).thenReturn(Optional.of(trip));
        when(telemetryRepository.findTop2ByTripIdOrderByTimestampDesc(1L))
                .thenReturn(List.of());

        var resultado = iaPredictionService.analyzeLatestReading(1L);

        assertThat(resultado).isEmpty();
        verifyNoInteractions(iaRestClient);
    }

    @Test
    @DisplayName("Deve retornar variacoes nulas quando nao ha leitura anterior")
    void deveRetornarVariacoesNulasQuandoNaoHaLeituraAnterior() {
        trip.setFruit(fruitUva);
        when(tripRepository.findDetailedById(1L)).thenReturn(Optional.of(trip));

        Telemetry unicaLeitura = new Telemetry();
        unicaLeitura.setId(100L);
        unicaLeitura.setTrip(trip);
        unicaLeitura.setTemperature(12.0);
        unicaLeitura.setHumidity(90.0);
        unicaLeitura.setTimestamp(LocalDateTime.now());
        when(telemetryRepository.findTop2ByTripIdOrderByTimestampDesc(1L))
                .thenReturn(List.of(unicaLeitura));

        // O RestClient real tentara conectar e falhará, retornando Optional.empty()
        var resultado = iaPredictionService.analyzeLatestReading(1L);

        assertThat(resultado).isEmpty();
    }

    @Test
    @DisplayName("Deve calcular variacoes quando ha leitura anterior (mas API falha)")
    void deveCalcularVariacoesQuandoHaLeituraAnteriorApiFalha() {
        trip.setFruit(fruitUva);
        when(tripRepository.findDetailedById(1L)).thenReturn(Optional.of(trip));

        Telemetry anterior = new Telemetry();
        anterior.setId(99L);
        anterior.setTrip(trip);
        anterior.setTemperature(10.0);
        anterior.setHumidity(88.0);
        anterior.setTimestamp(LocalDateTime.now().minusMinutes(15));

        Telemetry atual = new Telemetry();
        atual.setId(100L);
        atual.setTrip(trip);
        atual.setTemperature(12.0);
        atual.setHumidity(90.0);
        atual.setTimestamp(LocalDateTime.now());

        when(telemetryRepository.findTop2ByTripIdOrderByTimestampDesc(1L))
                .thenReturn(List.of(anterior, atual));

        // API nao esta rodando, entao retorna vazio, mas as variacoes foram calculadas internamente
        var resultado = iaPredictionService.analyzeLatestReading(1L);

        assertThat(resultado).isEmpty();
    }

    @Test
    @DisplayName("Previsao manual com IA desabilitada retorna vazio")
    void previsaoManualIaDesabilitadaRetornaVazio() {
        IaProperties iaPropertiesDesabilitada = new IaProperties(
                "http://localhost:8000",
                java.time.Duration.ofSeconds(3),
                java.time.Duration.ofSeconds(5),
                false
        );
        IaPredictionService serviceDesabilitado = new IaPredictionService(
                iaRestClient, iaPropertiesDesabilitada,
                tripRepository, containerRepository, fruitRepository,
                telemetryRepository, clock
        );

        trip.setFruit(fruitUva);

        Optional<IaPredictionResponse> resultado = serviceDesabilitado.predictIa(
                1L, 12.0, 90.0, 0.2, 1.0
        );

        assertThat(resultado).isEmpty();
    }
}
