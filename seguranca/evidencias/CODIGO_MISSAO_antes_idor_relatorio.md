# ANTES - IDOR (motorista A usando IDs do motorista B)

- Codigo de missao: **CODIGO_MISSAO**
- Gerado em: 2026-11-22T20:14:17.250186069-03:00
- Ambiente: Spring Boot + MockMvc + H2 em memoria, usuarios sinteticos

| Cenario | Requisicao | HTTP | Esperado | Resultado |
|---|---|---|---|---|
| A le as leituras do dispositivo B | `GET /api/trips/2/telemetry` | 200 | 403/404 | VULNERAVEL: permitido |
| A le os alertas da viagem B | `GET /api/trips/2/alerts` | 200 | 403/404 | VULNERAVEL: permitido |
| A le os dados do conteiner B | `GET /api/containers/2` | 200 | 403/404 | VULNERAVEL: permitido |
| A le os impactos do conteiner B | `GET /api/impacts/containers/2` | 200 | 403/404 | VULNERAVEL: permitido |
| A le os logs brutos do dispositivo B | `GET /api/thingspeak/logs?containerId=2` | 200 | 403/404 | VULNERAVEL: permitido |
| A baixa o PDF da viagem B | `GET /api/reports/trips/2/pdf` | 200 | 403/404 | VULNERAVEL: permitido |
| A altera a configuracao da viagem B | `PUT /api/trips/2` | 200 | 403/404 | VULNERAVEL: permitido |
| A manda comando de reprocessar IA na viagem B | `POST /api/ia/trips/2/reprocess` | 200 | 403/404 | VULNERAVEL: permitido |
| A manda comando de sincronizar o dispositivo B | `POST /api/impacts/sync?containerId=2&results=1` | 200 | 403/404 | VULNERAVEL: permitido |
| A manda comando de encerrar a viagem B | `POST /api/trips/2/end` | 200 | 403/404 | VULNERAVEL: permitido |
| A lista todas as viagens | `GET /api/trips` | 200 | So as viagens de A | VULNERAVEL: viagem B aparece na resposta |
| Contorno: A cria viagem para si com o conteiner B | `POST /api/trips` | 201 | 403/404 | VULNERAVEL: permitido |
| Contorno: A cria um usuario gestor (ROLE_COMPANY) | `POST /api/users` | 201 | 403/404 | VULNERAVEL: permitido |
