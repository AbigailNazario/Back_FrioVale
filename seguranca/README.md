# Segurança no Projeto Integrador: FrioVale

> **Código de missão: `CODIGO_MISSAO`**
> ⚠️ Substitua `CODIGO_MISSAO` pelo código de 6 caracteres enviado pelo professor. Ele precisa ser trocado neste README, nos nomes dos arquivos de `evidencias/` e na variável `MISSION_CODE` do backend, que entra nos logs de bloqueio. Veja os comandos em [testes/README.md](testes/README.md#trocar-o-codigo-de-missao).

Este documento é o relatório dos dois controles obrigatórios: **autenticação em dois fatores com OTP** e **proteção contra IDOR**. Os itens 3 a 7 aparecem uma vez para cada controle.

---

## 1. Identificação

| Integrante | Usuário do GitHub |
|---|---|
| `[PREENCHER]` | `[PREENCHER]` |
| `[PREENCHER]` | `[PREENCHER]` |
| `[PREENCHER]` | `[PREENCHER]` |

- **Projeto do PI:** FrioVale (monitoramento da cadeia fria de frutas no Vale do São Francisco)
- **Repositórios:** backend (este repositório) e frontend `[PREENCHER URL do repositório Front_FrioVale]`
- **Código de missão:** `CODIGO_MISSAO`

## 2. O projeto

O FrioVale monitora contêineres refrigerados que transportam frutas. Em cada contêiner, um **ESP32** lê temperatura e umidade, GPS e um acelerômetro (impactos e inclinação) e publica os dados em um canal do **ThingSpeak**. O **backend** (Java 17, Spring Boot 3.3.5, MySQL no Railway) consulta o ThingSpeak a cada 15 s, grava as leituras, gera alertas quando a temperatura sai da faixa da fruta e expõe uma API REST e um WebSocket (STOMP). O **frontend** (React 18, TypeScript e Vite, como PWA) é o painel do gestor e do motorista.

```
ESP32 (contêiner) ──HTTP──> ThingSpeak <──polling 15 s── Spring Boot API ──JPA──> MySQL (Railway)
                                                          ├─ REST /api/** (JWT)
                                                          ├─ STOMP /ws (alertas)
                                                          └─ API de IA (FastAPI)
                                                React PWA ──fetch──┘
```

**Perfis:** o **gestor** (`ROLE_COMPANY`) administra a frota. O **motorista** (`ROLE_DRIVER`) acompanha a viagem e o contêiner atribuídos a ele.

---

## CONTROLE 1: Autenticação em dois fatores (TOTP)

### 3. A ameaça

Antes do controle, **a senha bastava** para entrar: `POST /api/auth/login` devolvia o token de acesso na hora. Quem tivesse a senha de um gestor (vazamento, senha reutilizada, phishing, força bruta) teria o painel inteiro: cadastrar e excluir contêineres, trocar o canal IoT de um dispositivo, encerrar viagens, criar usuários e ler todas as leituras de temperatura. Na prática isso permite esconder uma quebra da cadeia fria e perder uma carga, além de vazar dados de motoristas (LGPD).

### 4. O controle

**O que foi implementado:** segundo fator TOTP (RFC 6238) com códigos de 6 dígitos que mudam a cada 30 s, compatível com Google Authenticator, Microsoft Authenticator e Authy. O cadastro é feito por QR Code, e cada usuário recebe 10 códigos de recuperação de uso único. **Toda a verificação acontece no servidor.**

**Fluxo:**
1. `POST /api/auth/login` com a senha correta **não devolve mais o token de acesso**. Devolve só um `mfaToken`, que vale 5 min e tem a claim `token_use=mfa`.
2. No primeiro acesso, `POST /api/auth/2fa/setup` gera o segredo e o QR Code. Em seguida, `POST /api/auth/2fa/confirm` valida o primeiro código, ativa o 2FA e entrega os códigos de recuperação.
3. Nos acessos seguintes, `POST /api/auth/2fa/verify` (código do app) ou `POST /api/auth/2fa/recovery` (código de recuperação) devolve o token de acesso (`token_use=access`).
4. Já logado, `POST /api/auth/2fa/recovery-codes/regenerate` gera códigos novos e invalida todos os anteriores.

**Onde está no código:**

| Arquivo | O que faz |
|---|---|
| [TwoFactorService.java](../src/main/java/com/pi/frioVale/service/TwoFactorService.java) | Regras do 2FA. **A linha que decide** é `checkTotp()` (linha ~191): janela de tempo (l. ~199), código errado ou expirado (l. ~207), **replay** (l. ~211). Uso único da recuperação: l. ~161–165. Recadastro bloqueado: l. ~93 |
| [SecurityConfig.java](../src/main/java/com/pi/frioVale/config/SecurityConfig.java) | `/api/auth/2fa/*` aceita só `TOKEN_MFA` (l. ~62). Todo o resto exige `TOKEN_ACCESS` (l. ~65). O `AccessDeniedHandler` registra a tentativa no log |
| [JwtConfig.java](../src/main/java/com/pi/frioVale/config/JwtConfig.java) | Converte a claim `token_use` na authority `TOKEN_ACCESS` ou `TOKEN_MFA`. Token antigo, sem a claim, não recebe nenhuma das duas |
| [JwtTokenService.java](../src/main/java/com/pi/frioVale/security/JwtTokenService.java) | Emite o token de acesso (`token_use=access`) e o intermediário (`issueMfa`) |
| [AuthService.java](../src/main/java/com/pi/frioVale/service/AuthService.java) | Login e cadastro devolvem só o desafio do 2FA |
| [TotpSecretCipher.java](../src/main/java/com/pi/frioVale/security/TotpSecretCipher.java) | Cifra o segredo TOTP com AES-256-GCM antes de gravar no banco |
| [TwoFactorController.java](../src/main/java/com/pi/frioVale/controller/TwoFactorController.java) | Rotas `/api/auth/2fa/*` |
| [User.java](../src/main/java/com/pi/frioVale/domain/User.java), [RecoveryCode.java](../src/main/java/com/pi/frioVale/domain/RecoveryCode.java) | Colunas `totp_*` e `otp_*` e a tabela `user_recovery_codes` (criadas pelo `ddl-auto=update`) |
| [StompAuthChannelInterceptor.java](../src/main/java/com/pi/frioVale/security/StompAuthChannelInterceptor.java) | O WebSocket também só aceita o token de acesso |
| [SecurityEventLogger.java](../src/main/java/com/pi/frioVale/security/SecurityEventLogger.java) | Log `[CODIGO_MISSAO] OTP BLOQUEADO ...` |
| Frontend: `src/components/TwoFactorStep.tsx`, `src/pages/Login.tsx`, `src/api.ts`, `src/App.tsx`, `src/pages/Profile.tsx` | QR Code, campo do código, opção de código de recuperação, mensagens de erro, geração de novos códigos |

**Decisões técnicas:**
- **Biblioteca `dev.samstevens.totp` 1.7.1:** gera o segredo (160 bits), o código HMAC-SHA1 e o QR Code. A comparação, a janela e o anti-replay foram escritos no projeto para ficarem visíveis e alteráveis ao vivo.
- **Token intermediário com claim `token_use`:** a separação entre "senha ok" e "2FA ok" fica no próprio JWT assinado, e o `SecurityConfig` barra o `mfaToken` em qualquer rota da API. O frontend não tem como pular essa etapa.
- **Anti-replay por passo de tempo:** o banco guarda `totp_last_step`. Só é aceito um código de passo **maior** que o último usado. O usuário é lido com `SELECT ... FOR UPDATE` para que duas requisições simultâneas com o mesmo código não passem.
- **Janela `allowed-discrepancy=1`:** aceita ±30 s de diferença de relógio do celular. É configurável em `application.properties`.
- **Segredo cifrado (AES-GCM):** a chave fica em `FRIOVALE_OTP_ENCRYPTION_KEY`, fora do repositório. **Sem a variável, a aplicação não sobe** (falha segura).
- **Códigos de recuperação:** 10 códigos aleatórios de 10 caracteres (cerca de 50 bits), gravados só como **hash SHA-256**. O código usado ganha `used_at` e não vale de novo. A resposta é a mesma para "inexistente" e "já usado".
- **Força bruta:** depois de 5 erros seguidos, o 2FA daquele usuário fica bloqueado por 5 min (HTTP 429).
- **Recadastro bloqueado:** quem só sabe a senha não consegue gerar um novo QR Code para o próprio celular (HTTP 409).
- **Cadastro público:** `/register` também só devolve o `mfaToken`, para não virar um atalho que pula o 2FA.

### 5. Como testar

Veja o passo a passo completo em [testes/README.md](testes/README.md). Resumo:

```powershell
# Testes automatizados (H2 em memória, usuários sintéticos - não usa o banco real)
.\mvnw.cmd test -Dtest=TwoFactorAuthIntegrationTest
# Teste manual contra o backend rodando (pede e-mail, senha e o código do celular)
powershell -ExecutionPolicy Bypass -File .\seguranca\testes\teste-manual.ps1 -ApiUrl http://localhost:8080
```

### 6. Resultado

| Caso (PDF, seção 5.1) | Antes | Depois |
|---|---|---|
| Senha certa, sem código | **200 + token de acesso** | 200 só com `mfaToken`; esse token em rota protegida dá **403** |
| Código errado | (não existia 2º fator) | **401** |
| Código vencido (90 s atrás) | — | **401** |
| Código válido | — | **200** + token de acesso |
| Código já usado (replay) | — | **401** |
| Recuperação: 1º uso | — | **200** |
| Recuperação: reutilizada | — | **401** |
| Contornos: token antigo, recadastro com a senha, força bruta, sem token | — | 403 / 409 / 429 / 401 |

**Evidências:**
- Antes: [relatório](evidencias/CODIGO_MISSAO_antes_otp_relatorio.md), [teste manual](evidencias/CODIGO_MISSAO_antes_teste_manual.txt), [Surefire](evidencias/CODIGO_MISSAO_antes_surefire.txt)
- Depois: [relatório automatizado](evidencias/CODIGO_MISSAO_depois_otp_relatorio.md), [teste manual](evidencias/CODIGO_MISSAO_depois_teste_manual.txt), [log do backend com o código de missão](evidencias/CODIGO_MISSAO_depois_teste_manual_backend.log), prints [QR Code](evidencias/CODIGO_MISSAO_depois_otp_01_qrcode_cadastro.png), [código errado](evidencias/CODIGO_MISSAO_depois_otp_02_codigo_errado.png), [códigos de recuperação](evidencias/CODIGO_MISSAO_depois_otp_03_codigos_recuperacao.png), [replay negado](evidencias/CODIGO_MISSAO_depois_otp_06_replay_negado.png), [recuperação reutilizada](evidencias/CODIGO_MISSAO_depois_otp_08_recuperacao_reutilizada.png)
- Vídeo de até 90 s: `evidencias/CODIGO_MISSAO_depois_otp_video.mp4` **(PENDENTE: a equipe grava no projeto real)**

> As evidências acima foram geradas com o código real do FrioVale rodando localmente com banco H2 e usuários sintéticos. As evidências no ambiente da equipe (URL real, ESP32 ligado) estão listadas como pendentes em [evidencias/README.md](evidencias/README.md).

### 7. Limitações

- **Não protege contra phishing em tempo real:** um site falso que repassa senha e código na hora consegue entrar. O próximo passo seria WebAuthn/passkeys.
- **Celular comprometido ou segredo copiado** gera códigos válidos. Um vazamento do banco **junto com** a chave AES também expõe os segredos.
- **O token de acesso ainda dura 12 h e fica em `localStorage`:** se for roubado por XSS, vale até expirar. Não existe revogação de sessão.
- **O login por senha não tem limite de tentativas.** Só o 2º fator tem bloqueio. Próximo passo: rate limiting por IP e e-mail no `/login`.
- **Não existe redefinição do 2FA pelo gestor:** quem perder o celular **e** os códigos depende de alguém limpar `totp_*` no banco.
- O bloqueio temporário pode ser usado para impedir um usuário legítimo de entrar por 5 min (quem já sabe a senha consegue provocá-lo).

---

## CONTROLE 2: Proteção contra IDOR

### 3. A ameaça

O backend identificava **quem** estava logado, mas não verificava **de quem** era o recurso pedido. O frontend filtrava as viagens do motorista **no navegador**, e a API entregava tudo. Bastava trocar o ID na URL para o motorista A **ler as leituras, alterar a configuração e mandar comandos** (encerrar viagem, sincronizar, reprocessar) para o contêiner do motorista B. Também dava para baixar o PDF de outra viagem, criar uma viagem para si com o contêiner de outro motorista e até criar um usuário gestor. Isso permite sabotar viagens alheias, apagar o rastro de um alerta e espionar a operação (rotas, horários e cargas).

### 4. O controle

**O que foi implementado:** verificação de dono **no servidor, a cada requisição**, em todas as rotas que recebem o ID de uma viagem (leituras, alertas, configuração e comandos) ou de um contêiner (dispositivo IoT). O usuário é sempre o `sub` do JWT de acesso validado, **nunca um ID enviado pelo cliente**. As listas também passaram a ser filtradas no servidor.

**Regra de dono:**
- Um motorista é dono das **viagens atribuídas a ele** (`trip.driver`).
- O **contêiner** é dele enquanto a viagem estiver `PLANNED` ou `IN_TRANSIT`.
- O gestor administra a frota e acessa tudo.

**Onde está no código:**

| Arquivo | O que faz |
|---|---|
| [AccessGuard.java](../src/main/java/com/pi/frioVale/security/AccessGuard.java) | **A linha que decide:** `requireTrip()` (l. ~71–79, consulta `existsByIdAndDriverId`), `requireContainer()` (l. ~82), `requireCompany()` (l. ~94). O bloqueio fica em `deny()` (l. ~143): log + **403** (`DENY_STATUS`, l. ~40). Também tem os filtros de listas `onlyOwnTrips` e `onlyOwnContainers` |
| [TripController.java](../src/main/java/com/pi/frioVale/controller/TripController.java) | Leituras, alertas, `PUT`, status, start, end e backfill com `requireTrip`. Criar viagem ou trocar contêiner/motorista só com `requireCompany` |
| [ContainerController.java](../src/main/java/com/pi/frioVale/controller/ContainerController.java) | `GET /{id}` com `requireContainer`. Lista filtrada. Escrita só para o gestor (agora com log) |
| [ImpactController.java](../src/main/java/com/pi/frioVale/controller/ImpactController.java), [ThingSpeakLogController.java](../src/main/java/com/pi/frioVale/controller/ThingSpeakLogController.java) | Impactos, logs brutos e comandos de sincronização por dono |
| [IaPredictionController.java](../src/main/java/com/pi/frioVale/controller/IaPredictionController.java) | A checagem fica **fora** do `try/catch` genérico, que antes engoliria o bloqueio |
| [ReportController.java](../src/main/java/com/pi/frioVale/controller/ReportController.java) | O PDF da viagem passa pelo dono. O relatório geral e as estatísticas de motoristas ficam só para o gestor |
| [AlertController.java](../src/main/java/com/pi/frioVale/controller/AlertController.java), [DashboardService.java](../src/main/java/com/pi/frioVale/service/DashboardService.java) | Alertas e dashboard filtrados no servidor |
| [UserController.java](../src/main/java/com/pi/frioVale/controller/UserController.java) | `POST /api/users` só para o gestor (contorno: o motorista não vira gestor) |
| [TripRepository.java](../src/main/java/com/pi/frioVale/repository/TripRepository.java) | Consultas de dono |
| Frontend: `src/api.ts`, `src/App.tsx` | 403/404 viram "Acesso negado: este recurso não pertence ao seu usuário." e nenhum dado é exibido. 401 volta para o login |

**Decisões técnicas:**
- **Checagem no controller, explícita e centralizada no `AccessGuard`:** cada rota tem uma linha `accessGuard.requireX(id)` que dá para mostrar na apresentação, e proteger uma rota nova é **uma linha**.
- **403 também para ID inexistente:** o atacante não descobre quais viagens existem (sem enumeração). Para devolver 404, basta trocar `DENY_STATUS`.
- **O frontend não é a defesa:** os filtros no navegador foram mantidos por usabilidade, mas a regra vale na API.
- **Contornos fechados:** criar ou reatribuir viagem e criar usuário viraram operações só do gestor. Sem isso, o motorista A poderia se tornar "dono" do contêiner B ou virar gestor.
- **O dono continua usando normalmente:** o motorista B lê, configura e comanda o próprio dispositivo (testado).

### 5. Como testar

```powershell
.\mvnw.cmd test -Dtest=IdorProtectionIntegrationTest
powershell -ExecutionPolicy Bypass -File .\seguranca\testes\teste-manual.ps1 -ApiUrl http://localhost:8080
```

Para tentar à mão: entre como o motorista A, copie o token (DevTools → Application → Local Storage → `friovale.token`) e chame `GET /api/trips/{ID da viagem de B}/telemetry` com `Authorization: Bearer <token>`.

### 6. Resultado

| Tentativa do motorista A sobre o dispositivo de B | Antes | Depois |
|---|---|---|
| Ler leituras, alertas, impactos, logs brutos, IA e PDF de B | **200** | **403** |
| Alterar a configuração (viagem, status, canal IoT) | **200** | **403** |
| Comandos (encerrar, iniciar, sincronizar, reprocessar, backfill) | **200** | **403** |
| Listas (viagens, dashboard, contêineres, alertas) | **mostravam B** | filtradas no servidor |
| Contorno: criar viagem para si com o contêiner B | **201** | **403** |
| Contorno: criar usuário gestor | **201** | **403** |
| Contorno: ID inexistente | — | **403** (sem enumeração) |
| Dono (B) usando o próprio dispositivo | 200 | **200** |
| Log | — | `[CODIGO_MISSAO] IDOR BLOQUEADO userId=.. papel=ROLE_DRIVER recurso=trip:2 ...` |

**Evidências:**
- Antes: [relatório](evidencias/CODIGO_MISSAO_antes_idor_relatorio.md), [teste manual](evidencias/CODIGO_MISSAO_antes_teste_manual.txt)
- Depois: [relatório automatizado (41 verificações)](evidencias/CODIGO_MISSAO_depois_idor_relatorio.md), [terceiro usuário](evidencias/CODIGO_MISSAO_depois_idor_terceiro_usuario.md), [teste manual](evidencias/CODIGO_MISSAO_depois_teste_manual.txt), [log do backend](evidencias/CODIGO_MISSAO_depois_ui_backend_bloqueios.log), [tentativas pelo navegador do motorista A](evidencias/CODIGO_MISSAO_depois_ui_execucao.log), [print: dashboard de A só com a própria viagem](evidencias/CODIGO_MISSAO_depois_idor_01_dashboard_motorista_A.png)
- Vídeo de até 90 s: `evidencias/CODIGO_MISSAO_depois_idor_video.mp4` **(PENDENTE)**

### 7. Limitações

- **O gestor vê tudo:** não existe separação entre empresas. Dois gestores de empresas diferentes veriam a frota um do outro. O próximo passo seria uma coluna `company_id` em contêiner e usuário.
- **Dados de um contêiner antes da viagem do motorista:** enquanto o contêiner está atribuído, o motorista vê os logs brutos e os impactos do canal inteiro, incluindo períodos anteriores à viagem dele. Próximo passo: filtrar pela janela de datas da viagem.
- **`GET /api/users`** continua listando nome, e-mail e telefone de todos os usuários para qualquer usuário logado. É exposição de dados pessoais (LGPD), registrada como ameaça aberta.
- **Proteção por rota:** uma rota nova sem a linha do `AccessGuard` nasce vulnerável. O próximo passo seria um teste automatizado que percorre todas as rotas com `{id}`.
- **O canal ThingSpeak e o ESP32 não são cobertos:** quem tiver a Write API Key do canal consegue forjar leituras sem passar pela API.

---

Modelo de ameaças: [modelo-ameacas.md](modelo-ameacas.md) · Uso de IA: [uso-de-ia.md](uso-de-ia.md) · Testes: [testes/](testes/README.md) · Evidências: [evidencias/](evidencias/README.md) · Roteiro da apresentação: [apresentacao.md](apresentacao.md)
