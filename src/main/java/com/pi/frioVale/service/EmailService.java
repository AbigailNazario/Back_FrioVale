package com.pi.frioVale.service;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Serviço de envio de e-mails do FrioVale via API REST do Brevo (OkHttp).
 * Funciona no Render, onde conexões SMTP diretas são bloqueadas.
 *
 * Padrão adaptado do Sistema Senac (projeto PI): chamada HTTP POST para
 * https://api.brevo.com/v3/smtp/email com header api-key.
 */
@Service
public class EmailService {

    @Value("${brevo.api.key:}")
    private String apiKey;

    @Value("${brevo.sender.email:}")
    private String senderEmail;

    @Value("${brevo.sender.name:FrioVale}")
    private String senderName;

    private static final String SENDER_NAME = "FrioVale";
    private final OkHttpClient client = new OkHttpClient();

    /**
     * Envia as credenciais de acesso (senha provisória gerada automaticamente)
     * para o e-mail do usuário recém-cadastrado.
     */
    public void enviarCredenciais(String nome, String email, String senhaProvisoria) {
        String html = """
            <div style="font-family:Arial,sans-serif;max-width:600px;margin:auto">
              <h2 style="color:#0b7c8a">Bem-vindo ao FrioVale</h2>
              <p>Olá, <strong>%s</strong>!</p>
              <p>Sua conta foi criada. Use as credenciais abaixo para acessar o sistema:</p>
              <table style="background:#f4f4f4;padding:16px;border-radius:8px;width:100%%">
                <tr><td><strong>Email:</strong></td><td>%s</td></tr>
                <tr><td><strong>Senha provisória:</strong></td><td style="color:#cc0000">%s</td></tr>
              </table>
              <p style="color:#888;font-size:12px;margin-top:24px">
                Por segurança, altere sua senha no primeiro acesso.
              </p>
            </div>
            """.formatted(nome, email, senhaProvisoria);

        enviar(email, "FrioVale - Suas credenciais de acesso", html);
    }

    /**
     * Manda a chamada HTTP para a API Brevo.
     * Falhas de envio são logadas mas não propagam exceção para não interromper o cadastro.
     */
    private void enviar(String destinatario, String assunto, String htmlContent) {
        String json = """
            {
              "sender": { "name": "%s", "email": "%s" },
              "to": [ { "email": "%s" } ],
              "subject": "%s",
              "htmlContent": "%s"
            }
            """.formatted(
                senderName != null && !senderName.isBlank() ? senderName : SENDER_NAME,
                senderEmail,
                destinatario,
                assunto,
                htmlContent.replace("\"", "\\\"").replace("\n", "").replace("\r", "")
        );

        Request request = new Request.Builder()
            .url("https://api.brevo.com/v3/smtp/email")
            .addHeader("api-key", apiKey)
            .addHeader("Content-Type", "application/json")
            .post(RequestBody.create(json, MediaType.get("application/json")))
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                System.out.println("[EmailService] Email enviado para " + destinatario + " | Status: " + response.code());
            } else {
                System.err.println("[EmailService] Falha ao enviar para " + destinatario + " | Status: " + response.code());
                System.err.println("[EmailService] Resposta: " + (response.body() != null ? response.body().string() : "sem corpo"));
            }
        } catch (Exception e) {
            System.err.println("[EmailService] Erro ao enviar email para " + destinatario + ": " + e.getMessage());
        }
    }
}
