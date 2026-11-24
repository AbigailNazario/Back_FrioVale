package com.pi.frioVale.seguranca;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pi.frioVale.domain.Alert;
import com.pi.frioVale.domain.Telemetry;
import com.pi.frioVale.domain.Trip;
import com.pi.frioVale.domain.User;
import com.pi.frioVale.domain.enums.Role;
import com.pi.frioVale.domain.enums.TripStatus;
import com.pi.frioVale.repository.AlertRepository;
import com.pi.frioVale.repository.ContainerRepository;
import com.pi.frioVale.repository.FruitRepository;
import com.pi.frioVale.repository.TelemetryRepository;
import com.pi.frioVale.repository.TripRepository;
import com.pi.frioVale.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * TESTE DO "DEPOIS" - Controle 2: protecao contra IDOR.
 *
 * Mesmo cenario do teste do antes: motorista A tenta ler, configurar e comandar o
 * dispositivo/viagem do motorista B trocando o ID. Todas as tentativas devem receber 403,
 * o log deve conter o codigo de missao e o dono (B) continua usando normalmente.
 *
 * Gera target/seguranca/CODIGO_MISSAO_depois_idor_relatorio.md
 * Rodar: mvnw test -Dtest=IdorProtectionIntegrationTest
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class IdorProtectionIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired ContainerRepository containers;
    @Autowired FruitRepository fruits;
    @Autowired TripRepository trips;
    @Autowired TelemetryRepository telemetry;
    @Autowired AlertRepository alerts;
    @Autowired PasswordEncoder encoder;

    @Test
    void motoristaANaoAcessaDispositivoDoMotoristaB(CapturedOutput output) throws Exception {
        SecurityReport report = new SecurityReport("DEPOIS - Protecao contra IDOR (motorista A usando IDs do motorista B)");
        SyntheticData d = SyntheticData.create(users, containers, fruits, trips, telemetry, encoder, "idor");
        Telemetry readingB = telemetry.findFirstByTripIdOrderByTimestampDesc(d.tripB.getId()).orElseThrow();
        alerts.save(Alert.builder().telemetry(readingB).message("Alerta sintetico da viagem B")
                .resolved(false).createdAt(LocalDateTime.now()).build());

        String tokenA = TestAuth.accessToken(mvc, json, d.motoristaA.getEmail());
        String tokenB = TestAuth.accessToken(mvc, json, d.motoristaB.getEmail());
        String tokenGestor = TestAuth.accessToken(mvc, json, d.gestor.getEmail());

        long tripB = d.tripB.getId();
        long tripA = d.tripA.getId();
        long contB = d.containerB.getId();
        long fruitId = d.tripB.getFruit().getId();
        List<Integer> blocked = new ArrayList<>();

        // ---------------- 1 e 2: leituras e informacoes protegidas do dispositivo B
        blocked.add(attack(report, tokenA, "1. A consulta o dispositivo (conteiner) B", get("/api/containers/" + contB)));
        blocked.add(attack(report, tokenA, "2. A consulta as leituras do dispositivo B", get("/api/trips/" + tripB + "/telemetry")));
        blocked.add(attack(report, tokenA, "2. A consulta os alertas da viagem B", get("/api/trips/" + tripB + "/alerts")));
        blocked.add(attack(report, tokenA, "2. A consulta os impactos da viagem B", get("/api/impacts/trips/" + tripB)));
        blocked.add(attack(report, tokenA, "2. A consulta o resumo de impactos da viagem B", get("/api/impacts/trips/" + tripB + "/summary")));
        blocked.add(attack(report, tokenA, "2. A consulta os impactos do conteiner B", get("/api/impacts/containers/" + contB)));
        blocked.add(attack(report, tokenA, "2. A consulta os logs brutos do dispositivo B", get("/api/thingspeak/logs?containerId=" + contB)));
        blocked.add(attack(report, tokenA, "2. A consulta a analise de IA da viagem B", get("/api/ia/trips/" + tripB + "/prediction")));
        blocked.add(attack(report, tokenA, "2. A baixa o PDF com todas as leituras da viagem B", get("/api/reports/trips/" + tripB + "/pdf")));

        // ---------------- 3: configuracao
        blocked.add(attack(report, tokenA, "3. A altera a configuracao da viagem B", put("/api/trips/" + tripB)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IN_TRANSIT\",\"destination\":\"Alterado por A\"}")));
        blocked.add(attack(report, tokenA, "3. A altera o status da viagem B", put("/api/trips/" + tripB + "/status")
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"COMPLETED\"}")));
        blocked.add(attack(report, tokenA, "3. A altera o canal IoT do conteiner B", put("/api/containers/" + contB)
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"CONT-B-idor\",\"thingSpeakChannelId\":\"999\"}")));

        // ---------------- 4: comandos
        blocked.add(attack(report, tokenA, "4. A manda comando: encerrar viagem B", post("/api/trips/" + tripB + "/end")));
        blocked.add(attack(report, tokenA, "4. A manda comando: iniciar viagem B", post("/api/trips/" + tripB + "/start")));
        blocked.add(attack(report, tokenA, "4. A manda comando: backfill do acelerometro B", post("/api/trips/" + tripB + "/backfill-accelerometer")));
        blocked.add(attack(report, tokenA, "4. A manda comando: reprocessar IA da viagem B", post("/api/ia/trips/" + tripB + "/reprocess")));
        blocked.add(attack(report, tokenA, "4. A manda comando: reprocessar ultima leitura B", post("/api/ia/trips/" + tripB + "/reprocess-latest")));
        blocked.add(attack(report, tokenA, "4. A manda comando: predicao manual na viagem B",
                post("/api/ia/predict?tripId=" + tripB + "&temperatura=5&umidade=90")));
        blocked.add(attack(report, tokenA, "4. A manda comando: sincronizar impactos do dispositivo B",
                post("/api/impacts/sync?containerId=" + contB + "&results=1")));
        blocked.add(attack(report, tokenA, "4. A manda comando: sincronizar logs do dispositivo B",
                post("/api/thingspeak/logs/sync?containerId=" + contB + "&results=1")));
        blocked.add(attack(report, tokenA, "4. A manda comando: sincronizar TODOS os dispositivos",
                post("/api/thingspeak/logs/sync?results=1")));
        blocked.add(attack(report, tokenA, "4. A exclui o conteiner B", delete("/api/containers/" + contB)));

        // ---------------- tentativas de contorno
        blocked.add(attack(report, tokenA, "Contorno: A cria viagem para si com o conteiner B", post("/api/trips")
                .contentType(MediaType.APPLICATION_JSON).content("{\"containerId\":" + contB + ",\"driverId\":"
                        + d.motoristaA.getId() + ",\"fruitId\":" + fruitId + ",\"startNow\":false}")));
        blocked.add(attack(report, tokenA, "Contorno: A troca o conteiner da PROPRIA viagem para o B", put("/api/trips/" + tripA)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IN_TRANSIT\",\"containerId\":" + contB + "}")));
        blocked.add(attack(report, tokenA, "Contorno: A cria um usuario gestor", post("/api/users")
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Gestor Falso\",\"email\":\"falso.depois@teste.local\","
                        + "\"role\":\"ROLE_COMPANY\",\"password\":\"" + SyntheticData.PASSWORD + "\"}")));
        blocked.add(attack(report, tokenA, "Contorno: A gera relatorio geral com o ID da viagem B",
                get("/api/reports/pdf?mode=trip&ids=" + tripB)));
        blocked.add(attack(report, tokenA, "Contorno: ID inexistente (enumeracao)", get("/api/trips/999999/telemetry")));

        assertThat(blocked).as("toda tentativa do motorista A contra B deve ser bloqueada").containsOnly(403);

        // ---------------- listas: B nao pode aparecer para A
        listCheck(report, tokenA, "Lista de viagens de A nao contem a viagem B", "/api/trips", "\"id\":" + tripB);
        listCheck(report, tokenA, "Monitor de viagens ativas de A nao contem B", "/api/trips/active/monitor", "\"id\":" + tripB);
        listCheck(report, tokenA, "Dashboard de A nao contem a viagem B", "/api/dashboard/summary", "\"id\":" + tripB + ",");
        listCheck(report, tokenA, "Lista de conteineres de A nao contem o B", "/api/containers", "CONT-B-idor");
        listCheck(report, tokenA, "Lista de alertas de A nao contem o alerta de B", "/api/alerts", "Alerta sintetico da viagem B");

        // ---------------- nada foi alterado no recurso de B
        Trip afterB = trips.findDetailedById(tripB).orElseThrow();
        assertThat(afterB.getStatus()).isEqualTo(TripStatus.IN_TRANSIT);
        assertThat(afterB.getDestination()).isEqualTo("Salvador");
        assertThat(containers.findById(contB).orElseThrow().getThingSpeakChannelId()).isEqualTo("Bidor");
        report.add("Viagem e conteiner B intactos apos os ataques", "(consulta ao banco)", 0, "sem alteracao", "OK: nada mudou");

        // ---------------- 5: o dono (B) e o gestor continuam usando normalmente
        int own1 = allowed(report, tokenB, "5. B consulta as leituras do proprio dispositivo", get("/api/trips/" + tripB + "/telemetry"));
        int own2 = allowed(report, tokenB, "5. B consulta o proprio conteiner", get("/api/containers/" + contB));
        int own3 = allowed(report, tokenB, "5. B altera a configuracao da propria viagem", put("/api/trips/" + tripB)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IN_TRANSIT\",\"destination\":\"Salvador - Porto\"}"));
        int own4 = allowed(report, tokenB, "5. B baixa o PDF da propria viagem", get("/api/reports/trips/" + tripB + "/pdf"));
        MvcResult listB = mvc.perform(get("/api/trips").header("Authorization", "Bearer " + tokenB)).andReturn();
        boolean bSeesOwn = listB.getResponse().getContentAsString().contains("\"id\":" + tripB);
        report.add("5. B ve a propria viagem na lista", "GET /api/trips", listB.getResponse().getStatus(), "200 com a viagem B",
                bSeesOwn ? "OK: permitido" : "FALHOU");
        int gestor = allowed(report, tokenGestor, "Gestor acessa o dispositivo B (administra a frota)", get("/api/trips/" + tripB + "/telemetry"));
        int own5 = allowed(report, tokenB, "5. B manda comando: encerrar a propria viagem", post("/api/trips/" + tripB + "/end"));

        assertThat(List.of(own1, own2, own3, own4, own5, gestor)).containsOnly(200);
        assertThat(bSeesOwn).isTrue();

        // ---------------- log com o codigo de missao
        String expectedLog = "[" + SecurityReport.MISSION_CODE + "] IDOR BLOQUEADO userId=" + d.motoristaA.getId();
        assertThat(output.getOut()).contains(expectedLog);
        report.add("Log do bloqueio contem o codigo de missao", "(log da aplicacao)", 0, expectedLog, "OK: registrado");

        report.write("depois_idor_relatorio");
    }

    @Test
    void terceiroUsuarioNaoEnxergaDispositivosDosOutros() throws Exception {
        SecurityReport report = new SecurityReport("DEPOIS - IDOR: terceiro usuario (alteracao ao vivo)");
        SyntheticData d = SyntheticData.create(users, containers, fruits, trips, telemetry, encoder, "terceiro");
        String emailC = "motorista.c.terceiro@teste.local";
        users.save(User.builder().name("Motorista C terceiro").email(emailC).role(Role.ROLE_DRIVER)
                .password(encoder.encode(SyntheticData.PASSWORD)).build());
        String tokenC = TestAuth.accessToken(mvc, json, emailC);

        int a = attack(report, tokenC, "C consulta as leituras do dispositivo A", get("/api/trips/" + d.tripA.getId() + "/telemetry"));
        int b = attack(report, tokenC, "C consulta as leituras do dispositivo B", get("/api/trips/" + d.tripB.getId() + "/telemetry"));
        String list = mvc.perform(get("/api/trips").header("Authorization", "Bearer " + tokenC))
                .andReturn().getResponse().getContentAsString();
        report.add("C lista as viagens", "GET /api/trips", 200, "lista vazia", "[]".equals(list) ? "OK: lista vazia" : "FALHOU");
        report.write("depois_idor_terceiro_usuario");

        assertThat(List.of(a, b)).containsOnly(403);
        assertThat(list).isEqualTo("[]");
    }

    // ------------------------------------------------------------------ auxiliares

    private int attack(SecurityReport report, String token, String cenario, MockHttpServletRequestBuilder req) throws Exception {
        MvcResult r = mvc.perform(req.header("Authorization", "Bearer " + token)).andReturn();
        int status = r.getResponse().getStatus();
        report.add(cenario, route(r), status, "403/404", status == 403 || status == 404 ? "OK: bloqueado" : "FALHOU");
        return status;
    }

    private int allowed(SecurityReport report, String token, String cenario, MockHttpServletRequestBuilder req) throws Exception {
        MvcResult r = mvc.perform(req.header("Authorization", "Bearer " + token)).andReturn();
        int status = r.getResponse().getStatus();
        report.add(cenario, route(r), status, "200", status == 200 ? "OK: permitido" : "FALHOU");
        return status;
    }

    private void listCheck(SecurityReport report, String token, String cenario, String path, String forbidden) throws Exception {
        MvcResult r = mvc.perform(get(path).header("Authorization", "Bearer " + token)).andReturn();
        boolean leaked = r.getResponse().getContentAsString().contains(forbidden);
        report.add(cenario, "GET " + path, r.getResponse().getStatus(), "sem dados de B", leaked ? "FALHOU: vazou" : "OK: filtrado no servidor");
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        assertThat(leaked).as(cenario).isFalse();
    }

    private static String route(MvcResult r) {
        String q = r.getRequest().getQueryString();
        return r.getRequest().getMethod() + " " + r.getRequest().getRequestURI() + (q == null ? "" : "?" + q);
    }
}
