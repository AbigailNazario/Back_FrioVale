# Testes de antes e depois (código de missão `CODIGO_MISSAO`)

Todos os testes usam **apenas usuários sintéticos** e devem ser executados **somente no projeto da equipe**.

## Pré-requisitos

- Java 17 (o Maven vem junto, pelo `mvnw`)
- Para o teste manual: o backend rodando, um usuário A e um usuário B (motoristas) com uma viagem atribuída a cada um, e o aplicativo autenticador no celular
- Variáveis de ambiente do backend (veja `.env.example` na raiz): `FRIOVALE_JWT_SECRET`, `FRIOVALE_OTP_ENCRYPTION_KEY`, `MISSION_CODE` e as do banco

## 1. Testes automatizados (recomendado: reproduzem tudo sem ajuda)

Não usam o banco real. Sobem o Spring Boot com H2 em memória (`src/test/resources/application-test.properties`).

```powershell
# Windows, na raiz do backend
$env:MISSION_CODE = "CODIGO_MISSAO"     # troque pelo codigo oficial
.\mvnw.cmd test                                          # suite inteira (22 testes)
.\mvnw.cmd test -Dtest=TwoFactorAuthIntegrationTest      # so o 2FA
.\mvnw.cmd test -Dtest=IdorProtectionIntegrationTest     # so o IDOR
```

| Classe | O que prova |
|---|---|
| `src/test/java/com/pi/frioVale/seguranca/TwoFactorAuthIntegrationTest.java` | Os 7 casos do PDF (senha sem código, código errado, vencido, válido, replay, recuperação 1º uso, recuperação reutilizada) e os contornos: `mfaToken` em rota protegida, token antigo sem 2FA, recadastro só com a senha, força bruta, cadastro público, código antigo depois de regenerar |
| `src/test/java/com/pi/frioVale/seguranca/IdorProtectionIntegrationTest.java` | O motorista A tenta ler, configurar e comandar o dispositivo de B (27 tentativas → 403), listas filtradas, recurso de B intacto, B e o gestor usando normalmente, log com o código de missão, terceiro usuário sem acesso a nada |

Os relatórios Markdown saem em `target/seguranca/CODIGO_MISSAO_depois_*.md`. Copie-os para `seguranca/evidencias/`.

## 2. Teste do ANTES (código original)

O mesmo cenário, executado no código **antes** dos controles, está em [antes/AntesDosControlesTest.java](antes/AntesDosControlesTest.java). Ele **passa** quando o sistema está vulnerável (comprova o problema) e **falha** depois dos controles.

```powershell
git checkout <commit-anterior-aos-controles>
# copie antes/AntesDosControlesTest.java + src/test/.../seguranca/SecurityReport.java e SyntheticData.java
# e src/test/resources/application-test.properties + a dependencia h2 (escopo test) do pom.xml
.\mvnw.cmd test -Dtest=AntesDosControlesTest
git checkout main
```

## 3. Teste manual contra o backend rodando (antes e depois, mesmo script)

O script descobre sozinho se o sistema ainda entrega o token só com a senha (ANTES) ou se exige o 2º fator (DEPOIS). O resultado é salvo em `seguranca/evidencias/CODIGO_MISSAO_manual_<data>.txt`.

```powershell
cd seguranca\testes
powershell -ExecutionPolicy Bypass -File .\teste-manual.ps1 -ApiUrl http://localhost:8080 -MissionCode CODIGO_MISSAO
```
```bash
# Linux/macOS/Git Bash (requer curl e jq)
cd seguranca/testes && API_URL=http://localhost:8080 MISSION_CODE=CODIGO_MISSAO ./teste-manual.sh
```

O script pede: e-mail e senha do usuário A, um código antigo (opcional, para o caso "vencido"), o código atual do app, um código de recuperação (opcional) e o login do usuário B. **Na etapa de replay ele reenvia o mesmo código de propósito.** Antes de rodar, cadastre o 2FA dos usuários A e B pela tela web.

> O `teste-manual.sh` foi executado no ambiente de desenvolvimento (antes e depois). O `teste-manual.ps1` tem a mesma lógica, mas **ainda precisa ser executado pela equipe** no Windows.

## 4. Tentativa manual pela interface (para o vídeo)

1. Entre como motorista A e copie o `friovale.token` (DevTools → Application → Local Storage).
2. No console do navegador:
   ```js
   fetch('http://localhost:8080/api/trips/ID_DA_VIAGEM_DE_B/telemetry', { headers: { Authorization: 'Bearer ' + localStorage.getItem('friovale.token') } }).then(r => console.log(r.status))
   ```
3. Resultado esperado: `403`. No terminal do backend aparece `[CODIGO_MISSAO] IDOR BLOQUEADO ...`.

## Trocar o código de missão

```powershell
# na raiz do backend: renomeia as evidencias e troca o texto nos .md, .txt e .log
$code = "K7Q2M9"   # <- codigo oficial
Get-ChildItem seguranca -Recurse -File | Where-Object Name -like "*CODIGO_MISSAO*" | Rename-Item -NewName { $_.Name -replace "CODIGO_MISSAO", $code }
Get-ChildItem seguranca -Recurse -Include *.md,*.txt,*.log,*.ps1,*.sh | ForEach-Object { (Get-Content $_ -Raw -Encoding UTF8) -replace "CODIGO_MISSAO", $code | Set-Content $_ -NoNewline -Encoding UTF8 }
```

Depois defina `MISSION_CODE=<codigo>` no ambiente do backend (ou troque o padrão em `application.properties`) e **rode os testes de novo**. Os logs e relatórios gerados passam a sair com o código oficial, e essa é a evidência mais forte.
