package com.pi.frioVale.seguranca;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Faz o login completo (senha + cadastro do 2FA + primeiro codigo) e devolve o token de acesso. */
final class TestAuth {

    private TestAuth() {
    }

    static String accessToken(MockMvc mvc, ObjectMapper json, String email) throws Exception {
        String login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + SyntheticData.PASSWORD + "\"}"))
                .andReturn().getResponse().getContentAsString();
        String mfa = json.readTree(login).path("mfaToken").asText();

        String setup = mvc.perform(post("/api/auth/2fa/setup").header("Authorization", "Bearer " + mfa))
                .andReturn().getResponse().getContentAsString();
        Matcher m = Pattern.compile("[?&]secret=([^&]+)").matcher(json.readTree(setup).path("otpauthUri").asText());
        if (!m.find()) {
            throw new IllegalStateException("otpauth sem secret");
        }
        String secret = URLDecoder.decode(m.group(1), StandardCharsets.UTF_8);
        String code = TwoFactorAuthIntegrationTest.totp(secret, Instant.now().getEpochSecond() / 30);

        String confirm = mvc.perform(post("/api/auth/2fa/confirm").header("Authorization", "Bearer " + mfa)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode body = json.readTree(confirm);
        return body.path("auth").path("token").asText();
    }
}
