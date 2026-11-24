# DEPOIS - Protecao contra IDOR (motorista A usando IDs do motorista B)

- Codigo de missao: **CODIGO_MISSAO**
- Gerado em: 2026-11-22T20:29:23.503397332-03:00
- Ambiente: Spring Boot + MockMvc + H2 em memoria, usuarios sinteticos

| Cenario | Requisicao | HTTP | Esperado | Resultado |
|---|---|---|---|---|
| 1. A consulta o dispositivo (conteiner) B | `GET /api/containers/2` | 403 | 403/404 | OK: bloqueado |
| 2. A consulta as leituras do dispositivo B | `GET /api/trips/2/telemetry` | 403 | 403/404 | OK: bloqueado |
| 2. A consulta os alertas da viagem B | `GET /api/trips/2/alerts` | 403 | 403/404 | OK: bloqueado |
| 2. A consulta os impactos da viagem B | `GET /api/impacts/trips/2` | 403 | 403/404 | OK: bloqueado |
| 2. A consulta o resumo de impactos da viagem B | `GET /api/impacts/trips/2/summary` | 403 | 403/404 | OK: bloqueado |
| 2. A consulta os impactos do conteiner B | `GET /api/impacts/containers/2` | 403 | 403/404 | OK: bloqueado |
| 2. A consulta os logs brutos do dispositivo B | `GET /api/thingspeak/logs?containerId=2` | 403 | 403/404 | OK: bloqueado |
| 2. A consulta a analise de IA da viagem B | `GET /api/ia/trips/2/prediction` | 403 | 403/404 | OK: bloqueado |
| 2. A baixa o PDF com todas as leituras da viagem B | `GET /api/reports/trips/2/pdf` | 403 | 403/404 | OK: bloqueado |
| 3. A altera a configuracao da viagem B | `PUT /api/trips/2` | 403 | 403/404 | OK: bloqueado |
| 3. A altera o status da viagem B | `PUT /api/trips/2/status` | 403 | 403/404 | OK: bloqueado |
| 3. A altera o canal IoT do conteiner B | `PUT /api/containers/2` | 403 | 403/404 | OK: bloqueado |
| 4. A manda comando: encerrar viagem B | `POST /api/trips/2/end` | 403 | 403/404 | OK: bloqueado |
| 4. A manda comando: iniciar viagem B | `POST /api/trips/2/start` | 403 | 403/404 | OK: bloqueado |
| 4. A manda comando: backfill do acelerometro B | `POST /api/trips/2/backfill-accelerometer` | 403 | 403/404 | OK: bloqueado |
| 4. A manda comando: reprocessar IA da viagem B | `POST /api/ia/trips/2/reprocess` | 403 | 403/404 | OK: bloqueado |
| 4. A manda comando: reprocessar ultima leitura B | `POST /api/ia/trips/2/reprocess-latest` | 403 | 403/404 | OK: bloqueado |
| 4. A manda comando: predicao manual na viagem B | `POST /api/ia/predict?tripId=2&temperatura=5&umidade=90` | 403 | 403/404 | OK: bloqueado |
| 4. A manda comando: sincronizar impactos do dispositivo B | `POST /api/impacts/sync?containerId=2&results=1` | 403 | 403/404 | OK: bloqueado |
| 4. A manda comando: sincronizar logs do dispositivo B | `POST /api/thingspeak/logs/sync?containerId=2&results=1` | 403 | 403/404 | OK: bloqueado |
| 4. A manda comando: sincronizar TODOS os dispositivos | `POST /api/thingspeak/logs/sync?results=1` | 403 | 403/404 | OK: bloqueado |
| 4. A exclui o conteiner B | `DELETE /api/containers/2` | 403 | 403/404 | OK: bloqueado |
| Contorno: A cria viagem para si com o conteiner B | `POST /api/trips` | 403 | 403/404 | OK: bloqueado |
| Contorno: A troca o conteiner da PROPRIA viagem para o B | `PUT /api/trips/1` | 403 | 403/404 | OK: bloqueado |
| Contorno: A cria um usuario gestor | `POST /api/users` | 403 | 403/404 | OK: bloqueado |
| Contorno: A gera relatorio geral com o ID da viagem B | `GET /api/reports/pdf?mode=trip&ids=2` | 403 | 403/404 | OK: bloqueado |
| Contorno: ID inexistente (enumeracao) | `GET /api/trips/999999/telemetry` | 403 | 403/404 | OK: bloqueado |
| Lista de viagens de A nao contem a viagem B | `GET /api/trips` | 200 | sem dados de B | OK: filtrado no servidor |
| Monitor de viagens ativas de A nao contem B | `GET /api/trips/active/monitor` | 200 | sem dados de B | OK: filtrado no servidor |
| Dashboard de A nao contem a viagem B | `GET /api/dashboard/summary` | 200 | sem dados de B | OK: filtrado no servidor |
| Lista de conteineres de A nao contem o B | `GET /api/containers` | 200 | sem dados de B | OK: filtrado no servidor |
| Lista de alertas de A nao contem o alerta de B | `GET /api/alerts` | 200 | sem dados de B | OK: filtrado no servidor |
| Viagem e conteiner B intactos apos os ataques | `(consulta ao banco)` | 0 | sem alteracao | OK: nada mudou |
| 5. B consulta as leituras do proprio dispositivo | `GET /api/trips/2/telemetry` | 200 | 200 | OK: permitido |
| 5. B consulta o proprio conteiner | `GET /api/containers/2` | 200 | 200 | OK: permitido |
| 5. B altera a configuracao da propria viagem | `PUT /api/trips/2` | 200 | 200 | OK: permitido |
| 5. B baixa o PDF da propria viagem | `GET /api/reports/trips/2/pdf` | 200 | 200 | OK: permitido |
| 5. B ve a propria viagem na lista | `GET /api/trips` | 200 | 200 com a viagem B | OK: permitido |
| Gestor acessa o dispositivo B (administra a frota) | `GET /api/trips/2/telemetry` | 200 | 200 | OK: permitido |
| 5. B manda comando: encerrar a propria viagem | `POST /api/trips/2/end` | 200 | 200 | OK: permitido |
| Log do bloqueio contem o codigo de missao | `(log da aplicacao)` | 0 | [CODIGO_MISSAO] IDOR BLOQUEADO userId=2 | OK: registrado |
