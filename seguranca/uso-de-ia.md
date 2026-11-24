# Declaração de uso de inteligência artificial

> Projeto: FrioVale · Código de missão: `CODIGO_MISSAO`
> Os trechos marcados com **[EQUIPE PREENCHE]** dependem do que cada integrante realmente fez e **não podem ser preenchidos pela IA**.

## Ferramenta

- **Claude** (Anthropic), modelo Claude Opus 5.5, usado no aplicativo Claude.
- Período de uso: `[EQUIPE PREENCHE: datas]`

## Para que foi usada

1. **Compreensão dos requisitos:** leitura do PDF do professor e lista do que era obrigatório.
2. **Análise do código existente:** diagnóstico do backend (Spring Boot) e do frontend (React), mapeamento das rotas sem verificação de dono e da autenticação só com senha.
3. **Implementação:** proposta e escrita do código dos dois controles, com o plano aprovado pela equipe antes.
4. **Testes:** escrita dos testes automatizados (JUnit + MockMvc + H2), dos scripts manuais (`teste-manual.sh` e `teste-manual.ps1`) e execução local com usuários sintéticos.
5. **Documentação:** rascunho deste README, do modelo de ameaças e do roteiro da apresentação.

## Partes produzidas ou revisadas com a IA

| Parte | Arquivos |
|---|---|
| 2FA (backend) | `TwoFactorService`, `TwoFactorController`, `TotpSecretCipher`, `SecurityEventLogger`, `OtpProperties`, `SecurityProperties`, `RecoveryCode`, `RecoveryCodeRepository`, DTOs do 2FA, alterações em `SecurityConfig`, `JwtConfig`, `JwtTokenService`, `AuthService`, `AuthController`, `User`, `UserRepository`, `StompAuthChannelInterceptor`, `application.properties`, `pom.xml` |
| IDOR (backend) | `AccessGuard`, alterações nos controllers (Trip, Container, Impact, ThingSpeakLog, IaPrediction, Report, Alert, User), `DashboardService`, `TripRepository`, `AlertRepository`, `ImpactAlertRepository` |
| Frontend | `TwoFactorStep.tsx`, alterações em `Login.tsx`, `App.tsx`, `Profile.tsx`, `api.ts`, `styles.css` |
| Testes | `src/test/.../seguranca/*`, `application-test.properties`, `seguranca/testes/*` |
| Documentação | `seguranca/*.md` |

## O que a IA executou de fato (e o que não executou)

- Executou os testes automatizados **no código original**, para gerar a evidência do antes, e **depois** dos controles (22 testes passando: 15 que já existiam + 7 novos).
- Executou o fluxo no navegador (Chrome headless) e o `teste-manual.sh` contra o backend rodando **localmente com banco H2 e usuários sintéticos**.
- **Não** acessou o banco do Railway, o ThingSpeak, o ESP32 nem o repositório da equipe no GitHub.
- **Não** executou o `teste-manual.ps1`: o PowerShell não estava disponível no ambiente da IA. A lógica é a mesma do `.sh`, que foi executado.
- **Não** fez commits, pull requests nem a tag.

## O que a equipe conferiu, testou ou alterou

> **[EQUIPE PREENCHE]**: descreva com honestidade. Exemplos do que registrar: quem leu e entendeu cada arquivo; quem rodou `mvnw test` e em qual máquina; quem testou o login com o celular real (Google Authenticator, Authy etc.); quem rodou o `teste-manual.ps1` no backend real; o que foi alterado em relação ao que a IA propôs e por quê; quem gravou os vídeos.

| Integrante | O que conferiu / testou / alterou |
|---|---|
| `[PREENCHER]` | `[PREENCHER]` |
| `[PREENCHER]` | `[PREENCHER]` |
| `[PREENCHER]` | `[PREENCHER]` |

Todos os integrantes declaram que entendem e respondem por cada linha entregue, conforme a seção 7 do enunciado.
