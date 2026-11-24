# ANTES - Autenticacao (sem 2FA)

- Codigo de missao: **CODIGO_MISSAO**
- Gerado em: 2026-11-22T20:14:16.529820572-03:00
- Ambiente: Spring Boot + MockMvc + H2 em memoria, usuarios sinteticos

| Cenario | Requisicao | HTTP | Esperado | Resultado |
|---|---|---|---|---|
| Login com senha correta e SEM codigo OTP | `POST /api/auth/login` | 200 | Deveria exigir 2o fator | VULNERAVEL: token de acesso entregue so com a senha |
| Rota protegida com o token obtido so com senha | `GET /api/trips` | 200 | Deveria ser negado | VULNERAVEL: acesso liberado |
