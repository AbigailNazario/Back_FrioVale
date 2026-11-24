package com.pi.frioVale.seguranca;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pi.frioVale.domain.User;
import com.pi.frioVale.domain.enums.Role;
import com.pi.frioVale.repository.UserRepository;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.HashingAlgorithm;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * TESTE DO "DEPOIS" - Controle 1: autenticacao em dois fatores (TOTP).
 *
 * Cobre os casos exigidos no PDF (secao 5.1) e as tentativas de contorno:
 *  senha sem codigo, codigo errado, codigo vencido, codigo valido, replay,
 *  recuperacao uso unico, token intermediario em rota protegida, token antigo,
 *  recadastro do 2FA so com a senha e forca bruta.
 *
 * Gera target/seguranca/CODIGO_MISSAO_depois_otp_relatorio.md
 * Rodar: mvnw test -Dtest=TwoFactorAuthIntegrationTest
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TwoFactorAuthIntegrationTest {

    private static final SecurityReport REPORT = new SecurityReport("DEPOIS - Autenticacao em dois fatores (TOTP)");
    private static final int PERIOD = 30;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtEncoder jwtEncoder;

    @AfterAll
    static void writeReport() {
        REPORT.write("depois_otp_relatorio");
    }

    // ------------------------------------------------------------------ fluxo principal

    @Test
    @Order(1)
    void fluxoCompletoDoSegundoFator() throws Exception {
        String email = newUser("fluxo");

        // 1) senha correta e SEM codigo: nao recebe token de acesso
        MvcResult login = login(email);
        JsonNode challenge = body(login);
        int status = login.getResponse().getStatus();
        record("1. Login com senha correta e sem OTP", "POST /api/auth/login", status,
                "sem token de acesso", challenge.has("token") ? "FALHOU: token entregue" : "OK: so mfaToken (2FA pendente)");
        assertThat(status).isEqualTo(200);
        assertThat(challenge.has("token")).isFalse();
        assertThat(challenge.path("enrollmentRequired").asBoolean()).isTrue();
        String mfa = challenge.path("mfaToken").asText();

        int bypass = status(get("/api/trips"), mfa);
        record("Contorno: usar o mfaToken em rota protegida", "GET /api/trips", bypass, "403", verdict(bypass == 403));
        assertThat(bypass).isEqualTo(403);

        // cadastro do 2FA por QR Code
        MvcResult setup = mvc.perform(post("/api/auth/2fa/setup").header("Authorization", "Bearer " + mfa)).andReturn();
        JsonNode setupBody = body(setup);
        assertThat(setup.getResponse().getStatus()).isEqualTo(200);
        assertThat(setupBody.path("qrCodeDataUri").asText()).startsWith("data:image/png;base64,");
        String secret = secretFrom(setupBody.path("otpauthUri").asText());
        record("Cadastro: backend gera segredo e QR Code", "POST /api/auth/2fa/setup", 200, "QR Code", "OK: QR Code PNG + otpauth://");

        int wrongConfirm = status(post("/api/auth/2fa/confirm").contentType(MediaType.APPLICATION_JSON)
                .content(code(wrongCode(secret, currentStep()))), mfa);
        record("Confirmacao com codigo errado", "POST /api/auth/2fa/confirm", wrongConfirm, "401", verdict(wrongConfirm == 401));
        assertThat(wrongConfirm).isEqualTo(401);

        long step = currentStep();
        MvcResult confirm = mvc.perform(post("/api/auth/2fa/confirm").header("Authorization", "Bearer " + mfa)
                .contentType(MediaType.APPLICATION_JSON).content(code(totp(secret, step)))).andReturn();
        JsonNode confirmBody = body(confirm);
        assertThat(confirm.getResponse().getStatus()).isEqualTo(200);
        assertThat(confirmBody.path("recoveryCodes")).hasSize(10);
        String access = confirmBody.path("auth").path("token").asText();
        record("Confirmacao com o 1o codigo valido", "POST /api/auth/2fa/confirm", 200,
                "2FA ativo + 10 codigos de recuperacao", "OK: 2FA ativado, 10 codigos de uso unico");
        assertThat(status(get("/api/trips"), access)).isEqualTo(200);

        // 2) novo login: codigo errado
        String mfa2 = body(login(email)).path("mfaToken").asText();
        int wrong = verify(mfa2, wrongCode(secret, currentStep()));
        record("2. Login com OTP incorreto", "POST /api/auth/2fa/verify", wrong, "401", verdict(wrong == 401));
        assertThat(wrong).isEqualTo(401);

        // 3) codigo vencido (gerado 3 passos = 90 s atras, fora da janela de +/- 1 passo)
        int expired = verify(mfa2, totp(secret, currentStep() - 3));
        record("3. Login com OTP expirado (90 s atras)", "POST /api/auth/2fa/verify", expired, "401", verdict(expired == 401));
        assertThat(expired).isEqualTo(401);

        // 4) codigo valido (passo seguinte ao usado na confirmacao, dentro da janela)
        String valid = totp(secret, step + 1);
        MvcResult ok = mvc.perform(post("/api/auth/2fa/verify").header("Authorization", "Bearer " + mfa2)
                .contentType(MediaType.APPLICATION_JSON).content(code(valid))).andReturn();
        int okStatus = ok.getResponse().getStatus();
        String access2 = body(ok).path("token").asText();
        record("4. Login com OTP valido", "POST /api/auth/2fa/verify", okStatus, "200 + token de acesso", verdict(okStatus == 200));
        assertThat(okStatus).isEqualTo(200);
        assertThat(status(get("/api/users/current"), access2)).isEqualTo(200);

        // 5) replay: o mesmo codigo outra vez
        String mfa3 = body(login(email)).path("mfaToken").asText();
        int replay = verify(mfa3, valid);
        record("5. Reutilizar o OTP ja aceito (replay)", "POST /api/auth/2fa/verify", replay, "401", verdict(replay == 401));
        assertThat(replay).isEqualTo(401);

        // 6 e 7) codigo de recuperacao: funciona uma vez so
        String recovery = confirmBody.path("recoveryCodes").get(0).asText();
        MvcResult rec = mvc.perform(post("/api/auth/2fa/recovery").header("Authorization", "Bearer " + mfa3)
                .contentType(MediaType.APPLICATION_JSON).content("{\"recoveryCode\":\"" + recovery + "\"}")).andReturn();
        int recStatus = rec.getResponse().getStatus();
        record("6. Primeiro uso do codigo de recuperacao", "POST /api/auth/2fa/recovery", recStatus, "200", verdict(recStatus == 200));
        assertThat(recStatus).isEqualTo(200);
        assertThat(body(rec).has("token")).isTrue();

        String mfa4 = body(login(email)).path("mfaToken").asText();
        int recAgain = status(post("/api/auth/2fa/recovery").contentType(MediaType.APPLICATION_JSON)
                .content("{\"recoveryCode\":\"" + recovery + "\"}"), mfa4);
        record("7. Reutilizar o codigo de recuperacao", "POST /api/auth/2fa/recovery", recAgain, "401", verdict(recAgain == 401));
        assertThat(recAgain).isEqualTo(401);

        // contorno: com a senha (mfaToken) tentar recadastrar o 2FA no celular do atacante
        int reenroll = status(post("/api/auth/2fa/setup"), mfa4);
        record("Contorno: recadastrar o 2FA sabendo so a senha", "POST /api/auth/2fa/setup", reenroll, "409", verdict(reenroll == 409));
        assertThat(reenroll).isEqualTo(409);

        // contorno: token de acesso nao serve para as rotas de verificacao, e mfaToken nao gera novos codigos
        int mfaRegen = status(post("/api/auth/2fa/recovery-codes/regenerate"), mfa4);
        record("Contorno: mfaToken pedindo novos codigos de recuperacao", "POST /api/auth/2fa/recovery-codes/regenerate",
                mfaRegen, "403", verdict(mfaRegen == 403));
        assertThat(mfaRegen).isEqualTo(403);

        // invalidar codigos de recuperacao: os antigos deixam de valer
        MvcResult regen = mvc.perform(post("/api/auth/2fa/recovery-codes/regenerate")
                .header("Authorization", "Bearer " + access2)).andReturn();
        assertThat(regen.getResponse().getStatus()).isEqualTo(200);
        String oldUnused = confirmBody.path("recoveryCodes").get(1).asText();
        int oldAfterRegen = status(post("/api/auth/2fa/recovery").contentType(MediaType.APPLICATION_JSON)
                .content("{\"recoveryCode\":\"" + oldUnused + "\"}"), mfa4);
        record("Codigo antigo depois de gerar novos codigos", "POST /api/auth/2fa/recovery", oldAfterRegen, "401",
                verdict(oldAfterRegen == 401));
        assertThat(oldAfterRegen).isEqualTo(401);
    }

    // ------------------------------------------------------------------ contornos adicionais

    @Test
    @Order(2)
    void tokenAntigoSemTipoNaoAcessaAApi() throws Exception {
        String email = newUser("antigo");
        User user = users.findByEmailIgnoreCase(email).orElseThrow();
        // Token assinado corretamente, igual ao que o sistema emitia ANTES do 2FA (sem token_use)
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("friovale").issuedAt(now).expiresAt(now.plusSeconds(600))
                .subject(String.valueOf(user.getId())).claim("role", user.getRole().name()).build();
        String legacy = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        int s = status(get("/api/trips"), legacy);
        record("Contorno: token no formato antigo (sem 2FA)", "GET /api/trips", s, "403", verdict(s == 403));
        assertThat(s).isEqualTo(403);
    }

    @Test
    @Order(3)
    void semTokenNenhumRecebe401() throws Exception {
        int s = mvc.perform(get("/api/trips")).andReturn().getResponse().getStatus();
        record("Contorno: rota protegida sem token", "GET /api/trips", s, "401", verdict(s == 401));
        assertThat(s).isEqualTo(401);
    }

    @Test
    @Order(4)
    void forcaBrutaBloqueiaTemporariamente() throws Exception {
        String email = newUser("forca");
        String mfa = body(login(email)).path("mfaToken").asText();
        String secret = secretFrom(body(mvc.perform(post("/api/auth/2fa/setup").header("Authorization", "Bearer " + mfa))
                .andReturn()).path("otpauthUri").asText());
        long step = currentStep();
        assertThat(status(post("/api/auth/2fa/confirm").contentType(MediaType.APPLICATION_JSON)
                .content(code(totp(secret, step))), mfa)).isEqualTo(200);

        String mfa2 = body(login(email)).path("mfaToken").asText();
        for (int i = 0; i < 5; i++) {
            assertThat(verify(mfa2, wrongCode(secret, currentStep()))).isEqualTo(401);
        }
        // Mesmo com o codigo certo, durante o bloqueio a resposta e 429
        int locked = verify(mfa2, totp(secret, step + 1));
        record("Contorno: forca bruta (5 erros e depois o codigo certo)", "POST /api/auth/2fa/verify", locked,
                "429 (bloqueio temporario)", verdict(locked == 429));
        assertThat(locked).isEqualTo(429);
    }

    @Test
    @Order(5)
    void cadastroNovoTambemExige2FA() throws Exception {
        String email = "novo." + UUID.randomUUID().toString().substring(0, 8) + "@teste.local";
        MvcResult reg = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Usuario Sintetico\",\"email\":\"" + email + "\",\"phone\":\"(87) 90000-0000\","
                        + "\"password\":\"" + SyntheticData.PASSWORD + "\"}")).andReturn();
        JsonNode b = body(reg);
        int s = reg.getResponse().getStatus();
        record("Contorno: cadastro publico entregando acesso direto", "POST /api/auth/register", s,
                "201 sem token de acesso", verdict(s == 201 && !b.has("token") && b.path("enrollmentRequired").asBoolean()));
        assertThat(s).isEqualTo(201);
        assertThat(b.has("token")).isFalse();
        assertThat(b.path("enrollmentRequired").asBoolean()).isTrue();
    }

    // ------------------------------------------------------------------ auxiliares

    private String newUser(String tag) {
        String email = "otp." + tag + "." + UUID.randomUUID().toString().substring(0, 8) + "@teste.local";
        users.save(User.builder().name("Usuario OTP " + tag).email(email).role(Role.ROLE_DRIVER)
                .password(encoder.encode(SyntheticData.PASSWORD)).build());
        return email;
    }

    private MvcResult login(String email) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + SyntheticData.PASSWORD + "\"}")).andReturn();
    }

    private int verify(String mfa, String otp) throws Exception {
        return status(post("/api/auth/2fa/verify").contentType(MediaType.APPLICATION_JSON).content(code(otp)), mfa);
    }

    private int status(MockHttpServletRequestBuilder req, String token) throws Exception {
        return mvc.perform(req.header("Authorization", "Bearer " + token)).andReturn().getResponse().getStatus();
    }

    private JsonNode body(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static String code(String otp) {
        return "{\"code\":\"" + otp + "\"}";
    }

    private static long currentStep() {
        return Instant.now().getEpochSecond() / PERIOD;
    }

    /** Gera o codigo como o aplicativo autenticador faria (mesmo algoritmo, mesmo segredo). */
    static String totp(String secret, long step) throws Exception {
        return new DefaultCodeGenerator(HashingAlgorithm.SHA1, 6).generate(secret, step);
    }

    /** Um codigo de 6 digitos que nao e valido em nenhum passo da janela aceita. */
    static String wrongCode(String secret, long step) throws Exception {
        for (int candidate = 0; ; candidate++) {
            String c = String.format("%06d", candidate);
            boolean collides = false;
            for (long s = step - 25; s <= step + 2; s++) {
                if (c.equals(totp(secret, s))) {
                    collides = true;
                    break;
                }
            }
            if (!collides) {
                return c;
            }
        }
    }

    private static String secretFrom(String otpauthUri) {
        Matcher m = Pattern.compile("[?&]secret=([^&]+)").matcher(otpauthUri);
        assertThat(m.find()).isTrue();
        return URLDecoder.decode(m.group(1), StandardCharsets.UTF_8);
    }

    private static String verdict(boolean ok) {
        return ok ? "OK: conforme esperado" : "FALHOU";
    }

    private static void record(String cenario, String req, int status, String esperado, String resultado) {
        REPORT.add(cenario, req, status, esperado, resultado);
    }
}
