package com.pi.frioVale.seguranca;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Gera um relatorio Markdown com cada requisicao de ataque executada nos testes.
 * O arquivo sai em target/seguranca/ com o codigo de missao no nome, pronto para
 * ser copiado para seguranca/evidencias/.
 */
public final class SecurityReport {

    /** Codigo de missao da equipe. Substitua CODIGO_MISSAO pelo codigo oficial (ou exporte MISSION_CODE). */
    public static final String MISSION_CODE = resolveMissionCode();

    private final String title;
    private final List<String> rows = new ArrayList<>();

    public SecurityReport(String title) {
        this.title = title;
    }

    private static String resolveMissionCode() {
        String fromEnv = System.getenv("MISSION_CODE");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }
        return System.getProperty("mission.code", "CODIGO_MISSAO");
    }

    public void add(String cenario, String requisicao, int status, String esperado, String resultado) {
        rows.add("| %s | `%s` | %d | %s | %s |".formatted(cenario, requisicao, status, esperado, resultado));
    }

    public void write(String fileSuffix) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(title).append("\n\n");
        sb.append("- Codigo de missao: **").append(MISSION_CODE).append("**\n");
        sb.append("- Gerado em: ").append(OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)).append("\n");
        sb.append("- Ambiente: Spring Boot + MockMvc + H2 em memoria, usuarios sinteticos\n\n");
        sb.append("| Cenario | Requisicao | HTTP | Esperado | Resultado |\n");
        sb.append("|---|---|---|---|---|\n");
        rows.forEach(r -> sb.append(r).append("\n"));
        try {
            Path dir = Path.of("target", "seguranca");
            Files.createDirectories(dir);
            Path file = dir.resolve(MISSION_CODE + "_" + fileSuffix + ".md");
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Nao foi possivel gravar o relatorio", e);
        }
    }
}
