# Evidências (código de missão `CODIGO_MISSAO`)

> **Onde foram geradas:** com o código do FrioVale rodando localmente, banco **H2 em memória** e **usuários sintéticos** (nenhum dado real, nenhum acesso ao Railway). O enunciado (seção 7.2) exige evidências que mostrem **os dispositivos, a URL e os logs da equipe**. Por isso a seção "Pendentes" abaixo precisa ser feita no ambiente real antes da entrega.

## Antes dos controles (código original)

| Arquivo | O que mostra |
|---|---|
| [CODIGO_MISSAO_antes_otp_relatorio.md](CODIGO_MISSAO_antes_otp_relatorio.md) | O login só com senha entrega o token de acesso, e com ele `GET /api/trips` responde 200 |
| [CODIGO_MISSAO_antes_idor_relatorio.md](CODIGO_MISSAO_antes_idor_relatorio.md) | O motorista A lê, altera e comanda a viagem e o contêiner de B (todos 200); a lista mostra B; A cria viagem com o contêiner B e cria um gestor (201) |
| [CODIGO_MISSAO_antes_surefire.txt](CODIGO_MISSAO_antes_surefire.txt) | Execução do `AntesDosControlesTest` (passou = vulnerabilidade comprovada) |
| [CODIGO_MISSAO_antes_teste_manual.txt](CODIGO_MISSAO_antes_teste_manual.txt) | Saída do `teste-manual.sh` no código original: "ANTES: token de acesso só com a senha" e 7 ataques IDOR com HTTP 200 |

## Depois: 2FA

| Arquivo | O que mostra |
|---|---|
| [CODIGO_MISSAO_depois_otp_relatorio.md](CODIGO_MISSAO_depois_otp_relatorio.md) | Os 7 casos do PDF + 7 contornos, todos conforme o esperado |
| [CODIGO_MISSAO_depois_surefire_TwoFactorAuthIntegrationTest.txt](CODIGO_MISSAO_depois_surefire_TwoFactorAuthIntegrationTest.txt) | 5 testes, 0 falhas |
| [CODIGO_MISSAO_depois_teste_manual.txt](CODIGO_MISSAO_depois_teste_manual.txt) | Mesmo script do antes: 17 verificações OK (2FA e IDOR) |
| [CODIGO_MISSAO_depois_teste_manual_backend.log](CODIGO_MISSAO_depois_teste_manual_backend.log) | Linhas de log `[CODIGO_MISSAO] OTP BLOQUEADO` e `IDOR BLOQUEADO` geradas pelo teste manual |
| `CODIGO_MISSAO_depois_otp_01..08_*.png` | Prints da interface: QR Code, código errado, códigos de recuperação, pedido de código no login, código expirado, replay negado, recuperação aceita e reutilizada |
| [CODIGO_MISSAO_depois_ui_execucao.log](CODIGO_MISSAO_depois_ui_execucao.log) | Registro da execução no navegador: o token não é salvo só com a senha, as mensagens de erro e o resultado de cada passo |

## Depois: IDOR

| Arquivo | O que mostra |
|---|---|
| [CODIGO_MISSAO_depois_idor_relatorio.md](CODIGO_MISSAO_depois_idor_relatorio.md) | 41 verificações: 27 tentativas de A (leitura, configuração, comandos e contornos) → 403; listas filtradas; recurso de B intacto; B e o gestor → 200; log com o código de missão |
| [CODIGO_MISSAO_depois_idor_terceiro_usuario.md](CODIGO_MISSAO_depois_idor_terceiro_usuario.md) | Um terceiro motorista (C) não enxerga A nem B |
| [CODIGO_MISSAO_depois_surefire_IdorProtectionIntegrationTest.txt](CODIGO_MISSAO_depois_surefire_IdorProtectionIntegrationTest.txt) | 2 testes, 0 falhas |
| [CODIGO_MISSAO_depois_ui_backend_bloqueios.log](CODIGO_MISSAO_depois_ui_backend_bloqueios.log) | Log do backend durante o teste pela interface (inclui `IDOR BLOQUEADO` das chamadas feitas pelo navegador de A) |
| [CODIGO_MISSAO_depois_idor_01_dashboard_motorista_A.png](CODIGO_MISSAO_depois_idor_01_dashboard_motorista_A.png) | O dashboard do motorista A traz só a viagem dele (filtrado no servidor) |

## Pendentes (a equipe precisa produzir no ambiente real)

- [ ] `CODIGO_MISSAO_antes_*` com o **projeto real** (URL da equipe e dispositivos reais). Se a equipe não tiver mais o código original rodando, use as evidências acima e explique na apresentação.
- [ ] `CODIGO_MISSAO_depois_otp_video.mp4`: vídeo de até 90 s com o celular real (Google Authenticator/Authy) mostrando: senha sem código, código errado, código válido, replay e recuperação usada duas vezes.
- [ ] `CODIGO_MISSAO_depois_idor_video.mp4`: vídeo de até 90 s com o motorista A trocando o ID para o de B (403), o log `[CÓDIGO] IDOR BLOQUEADO` no terminal e B usando o próprio dispositivo com o ESP32 ligado.
- [ ] `CODIGO_MISSAO_depois_teste_manual_ps1.txt`: saída do `teste-manual.ps1` contra o backend da equipe.
- [ ] Depois de receber o código oficial: renomear os arquivos e **rodar os testes de novo** para que os logs saiam com o código verdadeiro (veja [../testes/README.md](../testes/README.md#trocar-o-codigo-de-missao)).
