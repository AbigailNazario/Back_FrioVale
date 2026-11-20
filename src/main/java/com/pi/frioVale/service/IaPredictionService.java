package com.pi.frioVale.service;

import com.pi.frioVale.config.IaProperties;
import com.pi.frioVale.domain.Container;
import com.pi.frioVale.domain.Fruit;
import com.pi.frioVale.domain.Telemetry;
import com.pi.frioVale.domain.Trip;
import com.pi.frioVale.dto.IaPredictionRequest;
import com.pi.frioVale.dto.IaPredictionResponse;
import com.pi.frioVale.exception.ResourceNotFoundException;
import com.pi.frioVale.repository.ContainerRepository;
import com.pi.frioVale.repository.FruitRepository;
import com.pi.frioVale.repository.TelemetryRepository;
import com.pi.frioVale.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Servico de integracao com a API Python de IA (FastAPI).
 * Chama o endpoint POST /prever para analisar leituras de telemetria.
 * Falhas de comunicacao nao derrubam o backend: retorna Optional.empty().
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IaPredictionService {

    private final RestClient iaRestClient;
    private final IaProperties iaProperties;
    private final TripRepository tripRepository;
    private final ContainerRepository containerRepository;
    private final FruitRepository fruitRepository;
    private final TelemetryRepository telemetryRepository;
    private final Clock clock;

    /**
     * Analise uma leitura de telemetria usando a IA.
     *
     * @param tripId ID da viagem
     * @param temperatura temperatura atual
     * @param umidade umidade atual
     * @param variacaoTemperatura variacao de temperatura em relacao a leitura anterior
     * @param variacaoUmidade variacao de umidade em relacao a leitura anterior
     * @return Optional com a resposta da IA, ou vazio se a API estiver indisponível
     */
    public Optional<IaPredictionResponse> predictIa(
            Long tripId,
            Double temperatura,
            Double umidade,
            Double variacaoTemperatura,
            Double variacaoUmidade
    ) {
        if (!iaProperties.enabled()) {
            log.debug("IA desabilitada por configuracao");
            return Optional.empty();
        }

        Trip trip = tripRepository.findDetailedById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Viagem", tripId));

        Fruit fruit = trip.getFruit();
        String tipoFruta = fruit.getName();
        String estagioMaturacao = inferirEstagioMaturacao(fruit);

        IaPredictionRequest request = new IaPredictionRequest(
                tipoFruta,
                estagioMaturacao,
                temperatura,
                umidade,
                variacaoTemperatura,
                variacaoUmidade
        );

        try {
            IaPredictionResponse response = iaRestClient.post()
                    .uri("/prever")
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new RuntimeException("Erro na requisicao IA: " + res.getStatusCode());
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new RuntimeException("Erro no servidor IA: " + res.getStatusCode());
                    })
                    .body(IaPredictionResponse.class);

            return Optional.ofNullable(response);
        } catch (Exception ex) {
            log.warn("Falha ao comunicar com a API de IA: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Analise as ultimas duas leituras de telemetria de uma viagem e retorne o resultado da IA.
     * Calcula as variacoes automaticamente com base na leitura anterior.
     *
     * @param tripId ID da viagem
     * @return Optional com o resultado da IA, ou vazio se nao houver dados suficientes
     */
    public Optional<IaPredictionResultDto> analyzeLatestReading(Long tripId) {
        Trip trip = tripRepository.findDetailedById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Viagem", tripId));

        // Buscar as duas leituras MAIS RECENTES (mais nova primeiro)
        List<Telemetry> leituras = telemetryRepository
                .findTop2ByTripIdOrderByTimestampDesc(tripId);

        if (leituras == null || leituras.isEmpty()) {
            return Optional.empty();
        }

        Telemetry atual = leituras.get(0);
        Telemetry anterior = leituras.size() > 1 ? leituras.get(1) : null;

        Double variacaoTemp = null;
        Double variacaoUmid = null;

        if (anterior != null
                && anterior.getTemperature() != null
                && atual.getTemperature() != null) {
            variacaoTemp = atual.getTemperature() - anterior.getTemperature();
        }

        if (anterior != null
                && anterior.getHumidity() != null
                && atual.getHumidity() != null) {
            variacaoUmid = atual.getHumidity() - anterior.getHumidity();
        }

        // Se nao houver leitura anterior, variacoes sao nulas (tratado explicitamente)
        Optional<IaPredictionResponse> iaResponse = predictIa(
                tripId,
                atual.getTemperature(),
                atual.getHumidity(),
                variacaoTemp,
                variacaoUmid
        );

        if (iaResponse.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new IaPredictionResultDto(
                atual.getId(),
                iaResponse.get().perfil(),
                iaResponse.get().anomalia(),
                iaResponse.get().resultado(),
                iaResponse.get().mensagem(),
                atual.getTemperature(),
                atual.getHumidity(),
                variacaoTemp,
                variacaoUmid,
                iaResponse.get().tipoFruta(),
                LocalDateTime.now(clock)
        ));
    }

    /**
     * Inferir o estagio de maturacao baseado no nome da fruta.
     * O Trip nao possui estagio de maturacao diretamente, entao inferimos:
     * - Manga com nome contendo "verde" -> verde
     * - Manga com nome contendo "madura" ou sem qualificador -> madura
     * - Uva -> nao aplica (usa perfil uva)
     */
    private String inferirEstagioMaturacao(Fruit fruit) {
        String nome = fruit.getName() != null ? fruit.getName().toLowerCase() : "";

        if (nome.contains("uva")) {
            return "mesa";
        }

        if (nome.contains("manga")) {
            if (nome.contains("verde")) {
                return "verde";
            }
            return "madura";
        }

        return "desconhecido";
    }

    /** DTO de retorno da analise de IA para uma leitura. */
    public record IaPredictionResultDto(
            Long telemetryId,
            String perfil,
            Boolean anomalia,
            String resultado,
            String mensagem,
            Double temperatura,
            Double umidade,
            Double variacaoTemperatura,
            Double variacaoUmidade,
            String tipoFruta,
            LocalDateTime analyzedAt
    ) {
    }
}
