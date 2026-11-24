# DEPOIS - Autenticacao em dois fatores (TOTP)

- Codigo de missao: **CODIGO_MISSAO**
- Gerado em: 2026-11-22T20:38:40.348704135-03:00
- Ambiente: Spring Boot + MockMvc + H2 em memoria, usuarios sinteticos

| Cenario | Requisicao | HTTP | Esperado | Resultado |
|---|---|---|---|---|
| 1. Login com senha correta e sem OTP | `POST /api/auth/login` | 200 | sem token de acesso | OK: so mfaToken (2FA pendente) |
| Contorno: usar o mfaToken em rota protegida | `GET /api/trips` | 403 | 403 | OK: conforme esperado |
| Cadastro: backend gera segredo e QR Code | `POST /api/auth/2fa/setup` | 200 | QR Code | OK: QR Code PNG + otpauth:// |
| Confirmacao com codigo errado | `POST /api/auth/2fa/confirm` | 401 | 401 | OK: conforme esperado |
| Confirmacao com o 1o codigo valido | `POST /api/auth/2fa/confirm` | 200 | 2FA ativo + 10 codigos de recuperacao | OK: 2FA ativado, 10 codigos de uso unico |
| 2. Login com OTP incorreto | `POST /api/auth/2fa/verify` | 401 | 401 | OK: conforme esperado |
| 3. Login com OTP expirado (90 s atras) | `POST /api/auth/2fa/verify` | 401 | 401 | OK: conforme esperado |
| 4. Login com OTP valido | `POST /api/auth/2fa/verify` | 200 | 200 + token de acesso | OK: conforme esperado |
| 5. Reutilizar o OTP ja aceito (replay) | `POST /api/auth/2fa/verify` | 401 | 401 | OK: conforme esperado |
| 6. Primeiro uso do codigo de recuperacao | `POST /api/auth/2fa/recovery` | 200 | 200 | OK: conforme esperado |
| 7. Reutilizar o codigo de recuperacao | `POST /api/auth/2fa/recovery` | 401 | 401 | OK: conforme esperado |
| Contorno: recadastrar o 2FA sabendo so a senha | `POST /api/auth/2fa/setup` | 409 | 409 | OK: conforme esperado |
| Contorno: mfaToken pedindo novos codigos de recuperacao | `POST /api/auth/2fa/recovery-codes/regenerate` | 403 | 403 | OK: conforme esperado |
| Codigo antigo depois de gerar novos codigos | `POST /api/auth/2fa/recovery` | 401 | 401 | OK: conforme esperado |
| Contorno: token no formato antigo (sem 2FA) | `GET /api/trips` | 403 | 403 | OK: conforme esperado |
| Contorno: rota protegida sem token | `GET /api/trips` | 401 | 401 | OK: conforme esperado |
| Contorno: forca bruta (5 erros e depois o codigo certo) | `POST /api/auth/2fa/verify` | 429 | 429 (bloqueio temporario) | OK: conforme esperado |
| Contorno: cadastro publico entregando acesso direto | `POST /api/auth/register` | 201 | 201 sem token de acesso | OK: conforme esperado |
