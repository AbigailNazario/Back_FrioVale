# Guia da apresentação: segurança do FrioVale (`CODIGO_MISSAO`)

> Material de estudo da equipe. **Qualquer integrante pode ser chamado.** Todos precisam conseguir abrir os arquivos citados e explicar as linhas marcadas.

---

## Parte 1: conceitos (explicação didática e técnica)

### O que é autenticação em dois fatores
Provar a identidade com **dois tipos diferentes** de prova: algo que você **sabe** (senha) e algo que você **tem** (o celular com o app autenticador). Se a senha vazar, o atacante ainda precisa do celular.

### O que é TOTP
*Time-based One-Time Password* (RFC 6238). No cadastro, servidor e celular passam a compartilhar um **segredo** (160 bits, em Base32). Os dois calculam o mesmo código sem se comunicar, porque usam o **mesmo relógio**.

### Como o código de 6 dígitos é gerado
1. `passo = segundos desde 1970 / 30`. O passo muda a cada 30 s.
2. `hash = HMAC-SHA1(segredo, passo)`, que dá 20 bytes.
3. *Truncamento dinâmico:* o último meio byte escolhe uma posição; dali saem 4 bytes que viram um número de 31 bits.
4. `código = número % 1.000.000`, completado com zeros à esquerda até 6 dígitos.

No código: `TwoFactorService.generate()` chama `DefaultCodeGenerator(HashingAlgorithm.SHA1, 6).generate(segredo, passo)`.

### Como funciona o QR Code
O QR Code é só um texto no formato
`otpauth://totp/FrioVale:email?secret=BASE32&issuer=FrioVale&algorithm=SHA1&digits=6&period=30`.
O app lê o texto e guarda o segredo. O backend gera esse texto e a imagem PNG em `TwoFactorService.setup()`. O segredo é gravado **cifrado** (AES-256-GCM, `TotpSecretCipher`) e só ativa o 2FA depois que o usuário prova que escaneou, digitando o primeiro código em `/confirm`.

### Como funcionam os códigos de recuperação
No `/confirm` o backend gera 10 códigos aleatórios (`XXXXX-XXXXX`). Eles aparecem **uma única vez**, e no banco fica só o **hash SHA-256** (tabela `user_recovery_codes`). Ao usar um código, o backend procura o hash ainda não usado, preenche `used_at` e libera o acesso. Na segunda vez o hash não é mais encontrado (`usedAt IS NULL` falha) e a resposta é 401. Em **Perfil → Gerar novos códigos**, todos os anteriores são apagados.

### Como funciona a proteção contra replay
O código vale por 30 s (e a janela aceita ±1 passo). Um código capturado poderia ser reenviado dentro desse tempo. Por isso o banco guarda `totp_last_step`, o passo do último código aceito. Um código só é aceito se o passo dele for **maior** que esse valor (`TwoFactorService.checkTotp`, linha ~211). O usuário é lido com `SELECT ... FOR UPDATE` (`UserRepository.findForUpdateById`), então duas requisições simultâneas com o mesmo código não passam juntas.

### Por que a senha sozinha não dá acesso
O login devolve um `mfaToken` com `token_use=mfa`. O `JwtConfig` transforma essa claim na authority `TOKEN_MFA`, e o `SecurityConfig` exige `TOKEN_ACCESS` em **todas** as rotas, exceto as do próprio 2FA. Então mesmo chamando a API direto, sem a tela, o `mfaToken` recebe **403**. O token de acesso só é emitido por `confirm`, `verify` e `recover`.

### O que é IDOR
*Insecure Direct Object Reference.* A API recebe o ID de um objeto (`/api/trips/7/telemetry`) e devolve o objeto **sem conferir se ele pertence a quem pediu**. A autenticação está certa (o usuário existe); o que falha é a **autorização por objeto**.

### Como um atacante exploraria isso no FrioVale (antes)
O motorista A entra normalmente, abre o DevTools e vê `GET /api/trips/1/telemetry`. Troca o 1 por 2 e recebe as leituras do motorista B. Com `PUT /api/trips/2` muda o destino; com `POST /api/trips/2/end` encerra a viagem de B; com `POST /api/users` cria um gestor para si. Tudo isso foi comprovado em `evidencias/CODIGO_MISSAO_antes_idor_relatorio.md`.

### Como o backend identifica o dono do dispositivo
- **Quem pede:** o `sub` do JWT de acesso, que o servidor validou pela assinatura HS256 (`AccessGuard.caller()`). Nunca um ID vindo do corpo, da query ou de um cabeçalho do cliente.
- **De quem é:** a viagem tem `driver_id`. `AccessGuard.requireTrip(id)` faz `tripRepository.existsByIdAndDriverId(id, usuarioDoToken)`. O contêiner é do motorista enquanto ele tiver uma viagem `PLANNED` ou `IN_TRANSIT` com ele (`existsByContainerIdAndDriverIdAndStatusIn`).
- **Gestor:** administra a frota e passa direto.

### Onde estão os controles

| Controle | Arquivo → função |
|---|---|
| 2FA: decisão | `service/TwoFactorService.java` → `checkTotp`, `recover`, `setup`, `ensureNotLocked`, `registerFailure` |
| 2FA: barreira das rotas | `config/SecurityConfig.java` → `securityFilterChain` (`hasAuthority`) |
| 2FA: tipo do token | `config/JwtConfig.java` → `jwtAuthenticationConverter`; `security/JwtTokenService.java` → `issue`, `issueMfa` |
| 2FA: segredo | `security/TotpSecretCipher.java` |
| IDOR: decisão | `security/AccessGuard.java` → `requireTrip`, `requireContainer`, `requireCompany`, `deny` |
| IDOR: aplicação | Uma linha `accessGuard.requireX(...)` em cada rota dos controllers |
| Logs | `security/SecurityEventLogger.java` → `otpBlocked`, `idorBlocked` |
| Frontend | `components/TwoFactorStep.tsx`, `pages/Login.tsx`, `api.ts` (`ApiError`, `ACCESS_DENIED_MESSAGE`) |

### Principais decisões técnicas (e o porquê)
1. **Token intermediário em vez de "flag no front":** a separação entre senha e 2FA fica assinada no JWT, e o front não consegue pular a etapa.
2. **Janela de ±1 passo:** tolera a diferença de relógio do celular sem abrir demais. Com 0, só o código atual vale.
3. **Segredo cifrado e chave em variável de ambiente:** um dump do banco sozinho não gera códigos.
4. **Hash dos códigos de recuperação:** um vazamento do banco não revela os códigos.
5. **Bloqueio depois de 5 erros:** 6 dígitos são 1 milhão de combinações; sem limite, dá para adivinhar.
6. **403 também para ID inexistente:** o atacante não descobre quais IDs existem.
7. **Checagem explícita em cada rota:** fácil de mostrar e de revisar; proteger uma rota nova é uma linha.

### Como os testes comprovam
- **O mesmo cenário** (A e B sintéticos) roda no código original (tudo 200, `AntesDosControlesTest`) e no código novo (tudo 403, `IdorProtectionIntegrationTest`).
- Os testes geram o código TOTP **com o mesmo algoritmo do celular**, então provam o caminho real: código válido, vencido (passo −3), replay (mesmo passo).
- Os testes verificam também o **log com o código de missão** (`CapturedOutput`) e que **o recurso de B não mudou** no banco.

---

## Parte 2: roteiro de 10 minutos

> Antes de começar: backend e frontend rodando, ESP32 ligado, celular com o app autenticador, os usuários A e B (motoristas) e o gestor com 2FA já cadastrado, **um terminal com o log do backend visível** e outro na raiz do backend. Aba aberta no GitHub em `seguranca/`.

| Tempo | Quem | O que fazer e falar |
|---|---|---|
| **0:00–1:00** Projeto e repositório | `[integrante 1]` | Mostrar o painel com o contêiner e as leituras chegando do ESP32. Abrir `seguranca/` no GitHub: README, modelo de ameaças, testes, evidências, código de missão no topo. |
| **1:00–3:00** Ameaças e controles | `[integrante 2]` | No `modelo-ameacas.md`: A1 (senha vazada) → 2FA; A2 (trocar o ID) → IDOR; A4 é o maior risco aberto (credencial do banco). Abrir `TwoFactorService.checkTotp` e `AccessGuard.requireTrip` e mostrar **a linha que bloqueia**. |
| **3:00–5:00** Demo 2FA | `[integrante 3]` | **Antes:** abrir `evidencias/CODIGO_MISSAO_antes_otp_relatorio.md` (token só com senha). **Depois, ao vivo:** login como A: pede o código; digitar errado (erro e log `OTP BLOQUEADO`); digitar o do celular (entra). **Contorno:** login de novo com o mesmo código (replay → recusado) ou usar um código de recuperação já usado. |
| **5:00–7:00** Demo IDOR | `[integrante 4]` | **Antes:** `CODIGO_MISSAO_antes_idor_relatorio.md` (A lia, alterava e encerrava a viagem de B). **Depois, ao vivo:** logado como A, no console: `fetch('/api/trips/<id B>/telemetry'...)` → **403**; mostrar `[CODIGO] IDOR BLOQUEADO` no terminal. **Contorno:** `PUT` na própria viagem trocando `containerId` para o de B → 403. Logar como B: vê e controla o próprio contêiner. Alternativa rápida: `.\mvnw.cmd test -Dtest=IdorProtectionIntegrationTest`. |
| **7:00–9:00** Alteração ao vivo | escolhido pelo professor | Ver a Parte 3. |
| **9:00–10:00** Perguntas | todos | Ver a Parte 4. |

**Plano B:** se o projeto falhar, mostrar os vídeos de `evidencias/` e rodar `.\mvnw.cmd test` (não depende do banco nem do ESP32).

---

## Parte 3: alterações ao vivo (ensaiar todas)

### a) Mudar a janela de validade do OTP
`src/main/resources/application.properties`:
```properties
friovale.otp.allowed-discrepancy=0   # antes: 1. Agora so vale o codigo do passo atual
```
Reiniciar o backend. Para explicar: com 1, o código vale cerca de 90 s (anterior, atual e seguinte); com 0, só os 30 s do passo atual.
⚠️ **Não mexa em `period-seconds`** na apresentação: o Google Authenticator ignora o `period` do QR Code e continua gerando de 30 em 30 s, então o login quebraria.
Para provar: digitar um código que acabou de expirar → recusado (log `CODIGO_EXPIRADO`).

### b) Invalidar os códigos de recuperação de um usuário
- **Pela tela:** Perfil → "Gerar novos códigos de recuperação" (os antigos deixam de valer).
- **Pelo banco** (usuário específico):
  ```sql
  UPDATE user_recovery_codes SET used_at = NOW() WHERE user_id = 3 AND used_at IS NULL;
  ```
- **Como rota de administração** (se o professor pedir código):
  ```java
  // UserController
  @DeleteMapping("/{id}/recovery-codes")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void invalidateRecoveryCodes(@PathVariable Long id) {
      accessGuard.requireCompany("user:" + id + ":recovery-codes"); // so o gestor
      recoveryCodeRepository.deleteAllByUserId(id);                 // precisa de @Transactional ou chamar via service
  }
  ```
  (Na prática: injetar `RecoveryCodeRepository` e chamar via `TwoFactorService`, dentro de `@Transactional`.)

### c) Criar um terceiro usuário e mostrar que ele não vê os dispositivos dos outros
1. Na tela de login → Cadastrar (entra como motorista) → escanear o QR Code → guardar os códigos.
2. O dashboard dele aparece vazio.
3. No console: `fetch('/api/trips/<id de A>/telemetry', ...)` → 403 e log `IDOR BLOQUEADO userId=<C>`.
4. Já está automatizado em `IdorProtectionIntegrationTest.terceiroUsuarioNaoEnxergaDispositivosDosOutros`.

### d) Proteger uma rota nova que recebe o ID do dispositivo
Exemplo: última leitura de uma viagem.
```java
// TripController
@GetMapping("/{id}/last-reading")
public TelemetryResponse lastReading(@PathVariable Long id) {
    accessGuard.requireTrip(id);   // <- a linha que protege (sem ela: IDOR)
    List<TelemetryResponse> all = telemetryService.getHistory(id);
    return all.isEmpty() ? null : all.get(all.size() - 1);
}
```
Para contêiner: `accessGuard.requireContainer(containerId);`. Para demonstrar, comente a linha (A consegue) e descomente (403).

### e) Trocar 403 por 404 no bloqueio do IDOR
`AccessGuard.java`: `public static final HttpStatus DENY_STATUS = HttpStatus.NOT_FOUND;`

### f) Mudar o limite de tentativas ou o tempo de bloqueio
`friovale.otp.max-attempts=3` e `friovale.otp.lock-minutes=10`.

---

## Parte 4: perguntas prováveis e respostas

**"Mostre a linha em que o controle decide bloquear."**
2FA: `TwoFactorService.checkTotp`. O laço da janela fica na linha ~199; o `registerFailure(... "CODIGO_INVALIDO"/"CODIGO_EXPIRADO")` na ~207; o `REPLAY` na ~211. A barreira das rotas é o `SecurityConfig`, linha ~65 (`anyRequest().hasAuthority(TOKEN_ACCESS)`). IDOR: `AccessGuard.requireTrip` (o `if` com `existsByIdAndDriverId`, linha ~76) e `deny()` na ~143.

**"O que acontece se a variável de ambiente estiver vazia?"**
- `FRIOVALE_OTP_ENCRYPTION_KEY` vazia: a aplicação **não sobe** (`TotpSecretCipher` lança `IllegalStateException`). É uma falha segura: o sistema nunca roda sem cifrar os segredos.
- `FRIOVALE_JWT_SECRET` vazia ou com menos de 32 caracteres: também não sobe (`JwtConfig`).
- `MISSION_CODE` vazia: usa o padrão `CODIGO_MISSAO` (`application.properties`).
- **Trocar** a chave AES depois que há usuários cadastrados: os segredos antigos não decifram mais e esses usuários precisam recadastrar o 2FA.

**"Como um atacante tentaria contornar o 2FA?"**
(1) Usar o `mfaToken` direto na API → 403. (2) Reenviar um código capturado → 401 (replay). (3) Força bruta dos 6 dígitos → bloqueio em 5 tentativas (429). (4) Com a senha, recadastrar o 2FA no celular dele → 409. (5) Token antigo, sem `token_use` → 403. (6) Cadastrar conta nova para obter token → `/register` só devolve o `mfaToken`. **O que funcionaria:** phishing em tempo real (site falso que repassa o código) ou roubar o token de acesso depois do login (XSS, `localStorage`).

**"Como contornar o IDOR?"**
Trocar IDs em todas as rotas (testamos 27); usar ID inexistente para enumerar (403 igual); criar uma viagem para si com o contêiner do outro ou trocar o contêiner da própria viagem (403, só o gestor); virar gestor por `POST /api/users` (403). **O que ainda funcionaria:** quem é gestor vê tudo; e uma rota nova criada sem a linha do `AccessGuard`.

**"O que este controle não protege?"**
Veja a seção 7 do README de cada controle e o `modelo-ameacas.md` (A4 credencial do banco e A5 dados IoT/ThingSpeak).

**"Por que esse valor de configuração?"**
- `allowed-discrepancy=1`: celulares têm alguns segundos de diferença de relógio; 1 passo (±30 s) é o padrão recomendado pela RFC 6238 e por quase todos os provedores.
- `max-attempts=5` / `lock-minutes=5`: 5 tentativas a cada 5 min ≈ 1.440 por dia contra 1.000.000 de códigos possíveis. Incomoda pouco o usuário legítimo.
- `mfa-token-minutes=5`: tempo para pegar o celular; curto para limitar o uso do token intermediário.
- 10 códigos de recuperação de 10 caracteres: cerca de 50 bits cada, impraticável de adivinhar com o bloqueio.

**"Por que validar no backend e não no front?"**
Porque o atacante não usa a tela: chama a API com curl ou Postman. Todo filtro no navegador pode ser ignorado. No FrioVale o front **já filtrava** as viagens do motorista, e mesmo assim o teste do antes mostrou o vazamento.

**"Por que 403 e não 404?"**
Os dois são aceitos pelo enunciado. Escolhemos 403 para manter o padrão que o projeto já usava no `ImpactController` e responder igual para ID inexistente (sem enumeração). Mudar é uma linha (`DENY_STATUS`).

**"Por que SHA-1 no TOTP, se SHA-1 é fraco?"**
A fraqueza do SHA-1 é colisão, e isso não afeta o HMAC-SHA1. É o algoritmo padrão da RFC e o único aceito por todos os apps autenticadores.

**"Onde aparece o código de missão?"**
No README, no nome de cada arquivo de `evidencias/`, e em **toda linha de log de bloqueio** (`[CODIGO] OTP BLOQUEADO` e `[CODIGO] IDOR BLOQUEADO`), vindo da propriedade `friovale.security.mission-code` / `MISSION_CODE` (`SecurityEventLogger`).
