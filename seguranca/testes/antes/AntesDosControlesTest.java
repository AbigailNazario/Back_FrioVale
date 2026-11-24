package com.pi.frioVale.seguranca;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pi.frioVale.repository.ContainerRepository;
import com.pi.frioVale.repository.FruitRepository;
import com.pi.frioVale.repository.TelemetryRepository;
import com.pi.frioVale.repository.TripRepository;
import com.pi.frioVale.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * TESTE DO "ANTES" (executar no codigo ORIGINAL, antes dos controles).
 *
 * Comprova as duas falhas registradas no modelo de ameacas:
 *  1. Login so com senha ja entrega o token de acesso (sem segundo fator).
 *  2. IDOR: o motorista A le, configura e comanda o dispositivo/viagem do motorista B
 *     apenas trocando o ID na requisicao.
 *
 * Depois dos controles este teste deixa de passar (o que e o objetivo).
 * Para repetir o antes: faca checkout do commit anterior aos controles, copie este
 * arquivo e os auxiliares (SecurityReport, SyntheticData) para src/test/java/com/pi/frioVale/seguranca/
 * e rode: mvnw test -Dtest=AntesDosControlesTest
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AntesDosControlesTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired ContainerRepository containers;
    @Autowired FruitRepository fruits;
    @Autowired TripRepository trips;
    @Autowired TelemetryRepository telemetry;
    @Autowired PasswordEncoder encoder;

    @Test
    void comportamentoAntesDosControles() throws Exception {
        SyntheticData d = SyntheticData.create(users, containers, fruits, trips, telemetry, encoder, "antes");

        // ---------------- OTP: login somente com senha ----------------
        SecurityReport otp = new SecurityReport("ANTES - Autenticacao (sem 2FA)");
        MvcResult login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + d.motoristaA.getEmail() + "\",\"password\":\"" + SyntheticData.PASSWORD + "\"}"))
                .andReturn();
        JsonNode body = json.readTree(login.getResponse().getContentAsString());
        boolean tokenEntregue = body.hasNonNull("token");
        otp.add("Login com senha correta e SEM codigo OTP", "POST /api/auth/login", login.getResponse().getStatus(),
                "Deveria exigir 2o fator", tokenEntregue ? "VULNERAVEL: token de acesso entregue so com a senha" : "token nao entregue");
        String tokenA = body.path("token").asText();

        int protegida = mvc.perform(get("/api/trips").header("Authorization", "Bearer " + tokenA))
                .andReturn().getResponse().getStatus();
        otp.add("Rota protegida com o token obtido so com senha", "GET /api/trips", protegida,
                "Deveria ser negado", protegida == 200 ? "VULNERAVEL: acesso liberado" : "negado");
        otp.write("antes_otp_relatorio");

        assertThat(tokenEntregue).isTrue();
        assertThat(protegida).isEqualTo(200);

        // ---------------- IDOR: motorista A atacando recursos do motorista B ----------------
        SecurityReport idor = new SecurityReport("ANTES - IDOR (motorista A usando IDs do motorista B)");
        long tripB = d.tripB.getId();
        long contB = d.containerB.getId();

        int s1 = call(idor, tokenA, "A le as leituras do dispositivo B", get("/api/trips/" + tripB + "/telemetry"));
        int s2 = call(idor, tokenA, "A le os alertas da viagem B", get("/api/trips/" + tripB + "/alerts"));
        int s3 = call(idor, tokenA, "A le os dados do conteiner B", get("/api/containers/" + contB));
        int s4 = call(idor, tokenA, "A le os impactos do conteiner B", get("/api/impacts/containers/" + contB));
        int s5 = call(idor, tokenA, "A le os logs brutos do dispositivo B", get("/api/thingspeak/logs?containerId=" + contB));
        int s6 = call(idor, tokenA, "A baixa o PDF da viagem B", get("/api/reports/trips/" + tripB + "/pdf"));
        int s7 = call(idor, tokenA, "A altera a configuracao da viagem B", put("/api/trips/" + tripB)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IN_TRANSIT\",\"destination\":\"Destino alterado por A\"}"));
        int s8 = call(idor, tokenA, "A manda comando de reprocessar IA na viagem B", post("/api/ia/trips/" + tripB + "/reprocess"));
        int s9 = call(idor, tokenA, "A manda comando de sincronizar o dispositivo B", post("/api/impacts/sync?containerId=" + contB + "&results=1"));
        int s10 = call(idor, tokenA, "A manda comando de encerrar a viagem B", post("/api/trips/" + tripB + "/end"));

        MvcResult lista = mvc.perform(get("/api/trips").header("Authorization", "Bearer " + tokenA)).andReturn();
        boolean vazou = lista.getResponse().getContentAsString().contains("\"id\":" + tripB);
        idor.add("A lista todas as viagens", "GET /api/trips", lista.getResponse().getStatus(),
                "So as viagens de A", vazou ? "VULNERAVEL: viagem B aparece na resposta" : "so as de A");

        // Contornos: o motorista A tenta virar "dono" do dispositivo B ou virar gestor
        long fruitId = d.tripB.getFruit().getId();
        int s11 = call(idor, tokenA, "Contorno: A cria viagem para si com o conteiner B", post("/api/trips")
                .contentType(MediaType.APPLICATION_JSON).content("{\"containerId\":" + contB + ",\"driverId\":"
                        + d.motoristaA.getId() + ",\"fruitId\":" + fruitId + ",\"startNow\":true}"));
        int s12 = call(idor, tokenA, "Contorno: A cria um usuario gestor (ROLE_COMPANY)", post("/api/users")
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Gestor Falso\",\"email\":\"falso.antes@teste.local\","
                        + "\"role\":\"ROLE_COMPANY\",\"password\":\"SenhaSintetica#2026\"}"));

        idor.write("antes_idor_relatorio");

        assertThat(new int[]{s1, s2, s3, s4, s5, s6, s7, s8, s9, s10}).containsOnly(200);
        assertThat(new int[]{s11, s12}).containsOnly(201);
        assertThat(vazou).isTrue();
    }

    private int call(SecurityReport report, String token, String cenario, MockHttpServletRequestBuilder req) throws Exception {
        MvcResult r = mvc.perform(req.header("Authorization", "Bearer " + token)).andReturn();
        int status = r.getResponse().getStatus();
        String rota = r.getRequest().getMethod() + " " + r.getRequest().getRequestURI()
                + (r.getRequest().getQueryString() == null ? "" : "?" + r.getRequest().getQueryString());
        report.add(cenario, rota, status, "403/404",
                status >= 200 && status < 300 ? "VULNERAVEL: permitido" : "bloqueado");
        return status;
    }
}
