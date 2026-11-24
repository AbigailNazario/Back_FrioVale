package com.pi.frioVale.controller;

import com.pi.frioVale.security.AccessGuard;
import com.pi.frioVale.dto.IaPredictionResponse;
import com.pi.frioVale.exception.ResourceNotFoundException;
import com.pi.frioVale.service.IaPredictionService;
import com.pi.frioVale.service.IaReprocessingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Endpoints de integracao com a API de IA (FastAPI Python). */
@RestController
@RequestMapping("/api/ia")
@RequiredArgsConstructor
public class IaPredictionController {

    private final IaPredictionService iaPredictionService;
    private final IaReprocessingService iaReprocessingService;
    private final AccessGuard accessGuard; // [Seguranca - IDOR]

    /** Verificar se a API de IA esta online e modelos carregados. */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        try {
            return ResponseEntity.ok(Map.of(
                    "status", "ok",
                    "iaService", "disponivel"
            ));
        } catch (Exception ex) {
            return ResponseEntity.ok(Map.of(
                    "status", "erro",
                    "iaService", "indisponivel: " + ex.getMessage()
            ));
        }
    }

    /** Listar modelos disponiveis na API de IA. */
    @GetMapping("/modelos")
    public ResponseEntity<Map<String, Object>> modelos() {
        try {
            return ResponseEntity.ok(Map.of(
                    "status", "ok",
                    "mensagem", "Consulte GET http://localhost:8000/modelos diretamente na API Python"
            ));
        } catch (Exception ex) {
            return ResponseEntity.ok(Map.of(
                    "status", "erro",
                    "mensagem", ex.getMessage()
            ));
        }
    }

    /** Obter predicao de IA para a ultima leitura de uma viagem. */
    @GetMapping("/trips/{tripId}/prediction")
    public ResponseEntity<Map<String, Object>> prediction(@PathVariable Long tripId) {
        accessGuard.requireTrip(tripId); // [Seguranca - IDOR] fora do try: o catch generico nao pode engolir o bloqueio
        try {
            var resultado = iaPredictionService.analyzeLatestReading(tripId);
            if (resultado.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                        "status", "sem_dados",
                        "mensagem", "Nao foi possivel analisar: verifique se ha leituras ou se a API de IA esta disponível"
                ));
            }
            var dto = resultado.get();
            Map<String, Object> resposta = new LinkedHashMap<>();
            resposta.put("status", "ok");
            resposta.put("telemetryId", dto.telemetryId());
            resposta.put("perfil", dto.perfil());
            resposta.put("tipoFruta", dto.tipoFruta());
            resposta.put("temperatura", dto.temperatura());
            resposta.put("umidade", dto.umidade());
            resposta.put("variacaoTemperatura", dto.variacaoTemperatura());
            resposta.put("variacaoUmidade", dto.variacaoUmidade());
            resposta.put("iaAnomalia", dto.anomalia());
            resposta.put("iaResultado", dto.resultado());
            resposta.put("iaMensagem", dto.mensagem());
            resposta.put("analyzedAt", dto.analyzedAt());
            return ResponseEntity.ok(resposta);
        } catch (ResourceNotFoundException ex) {
            return ResponseEntity.notFound().build();
        } catch (Exception ex) {
            return ResponseEntity.ok(Map.of(
                    "status", "erro",
                    "mensagem", ex.getMessage()
            ));
        }
    }

    /** Predicao manual de IA (para testes). */
    @PostMapping("/predict")
    public ResponseEntity<Map<String, Object>> predictManual(
            @RequestParam Long tripId,
            @RequestParam Double temperatura,
            @RequestParam Double umidade,
            @RequestParam(required = false) Double variacaoTemperatura,
            @RequestParam(required = false) Double variacaoUmidade
    ) {
        accessGuard.requireTrip(tripId); // [Seguranca - IDOR]
        try {
            Optional<IaPredictionResponse> resposta = iaPredictionService.predictIa(
                    tripId, temperatura, umidade, variacaoTemperatura, variacaoUmidade
            );
            if (resposta.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                        "status", "erro",
                        "mensagem", "API de IA indisponível"
                ));
            }
            var r = resposta.get();
            Map<String, Object> respostaMap = new LinkedHashMap<>();
            respostaMap.put("status", "ok");
            respostaMap.put("perfil", r.perfil());
            respostaMap.put("tipoFruta", r.tipoFruta());
            respostaMap.put("iaAnomalia", r.anomalia());
            respostaMap.put("iaResultado", r.resultado());
            respostaMap.put("iaMensagem", r.mensagem());
            respostaMap.put("valoresRecebidos", r.valoresRecebidos());
            return ResponseEntity.ok(respostaMap);
        } catch (ResourceNotFoundException ex) {
            return ResponseEntity.notFound().build();
        } catch (Exception ex) {
            return ResponseEntity.ok(Map.of(
                    "status", "erro",
                    "mensagem", ex.getMessage()
            ));
        }
    }

    /** Reprocessa SEMPRE a última leitura de uma viagem, garantindo análise no painel. */
    @PostMapping("/trips/{tripId}/reprocess-latest")
    public ResponseEntity<Map<String, Object>> reprocessLatest(@PathVariable Long tripId) {
        accessGuard.requireTrip(tripId); // [Seguranca - IDOR] comando
        try {
            boolean processado = iaReprocessingService.reprocessarUltimaLeitura(tripId);
            return ResponseEntity.ok(Map.of(
                    "status", "ok",
                    "tripId", tripId,
                    "processado", processado
            ));
        } catch (ResourceNotFoundException ex) {
            return ResponseEntity.notFound().build();
        } catch (Exception ex) {
            return ResponseEntity.ok(Map.of("status", "erro", "mensagem", ex.getMessage()));
        }
    }

    /** Reprocessa leituras pendentes de IA para uma viagem (reprocessamento automático). */
    @PostMapping("/trips/{tripId}/reprocess")
    public ResponseEntity<Map<String, Object>> reprocessTrip(@PathVariable Long tripId) {
        accessGuard.requireTrip(tripId); // [Seguranca - IDOR] comando
        try {
            int reprocessados = iaReprocessingService.reprocessarTrip(tripId);
            Map<String, Object> resposta = new LinkedHashMap<>();
            resposta.put("status", "ok");
            resposta.put("tripId", tripId);
            resposta.put("reprocessados", reprocessados);
            return ResponseEntity.ok(resposta);
        } catch (ResourceNotFoundException ex) {
            return ResponseEntity.notFound().build();
        } catch (Exception ex) {
            return ResponseEntity.ok(Map.of(
                    "status", "erro",
                    "mensagem", ex.getMessage()
            ));
        }
    }
}
