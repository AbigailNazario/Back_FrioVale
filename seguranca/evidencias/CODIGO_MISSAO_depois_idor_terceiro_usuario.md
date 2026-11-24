# DEPOIS - IDOR: terceiro usuario (alteracao ao vivo)

- Codigo de missao: **CODIGO_MISSAO**
- Gerado em: 2026-11-22T20:29:24.052980321-03:00
- Ambiente: Spring Boot + MockMvc + H2 em memoria, usuarios sinteticos

| Cenario | Requisicao | HTTP | Esperado | Resultado |
|---|---|---|---|---|
| C consulta as leituras do dispositivo A | `GET /api/trips/3/telemetry` | 403 | 403/404 | OK: bloqueado |
| C consulta as leituras do dispositivo B | `GET /api/trips/4/telemetry` | 403 | 403/404 | OK: bloqueado |
| C lista as viagens | `GET /api/trips` | 200 | lista vazia | OK: lista vazia |
