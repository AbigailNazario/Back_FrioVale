
# FRIOVALE | Backend API

<p align="center">
  <strong>Backend Services for Cold Chain Monitoring and IoT Data Management</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-17-orange" />
  <img src="https://img.shields.io/badge/Spring%20Boot-3.3.5-brightgreen" />
  <img src="https://img.shields.io/badge/Database-MySQL-blue" />
  <img src="https://img.shields.io/badge/Build-Maven-red" />
</p>

<details open>
<summary><strong>🇺🇸 English version</strong></summary>

## 1. About the project

FrioVale is an IoT-based solution designed to monitor environmental conditions in the cold chain used for fruit storage and transportation in the São Francisco Valley, Brazil.

This repository contains the backend application responsible for providing server-side services, data persistence, security components and integration capabilities for the FrioVale platform.

The backend is developed with Java and Spring Boot.

## 2. Main objectives

- Provide backend services for the FrioVale platform.
- Support data persistence through MySQL.
- Manage application entities using Spring Data JPA.
- Provide validation and security infrastructure.
- Support communication between the frontend and backend.
- Support integration with IoT telemetry sources.
- Provide a foundation for monitoring, querying and analyzing environmental data.

## 3. Technologies

| Technology | Purpose |
|---|---|
| Java 17 | Programming language |
| Spring Boot 3.3.5 | Backend framework |
| Spring Web | HTTP services |
| Spring Data JPA | Database persistence |
| MySQL Connector/J | MySQL connectivity |
| Spring Security | Security infrastructure |
| OAuth2 Resource Server | Token-based resource security |
| Spring Validation | Input validation |
| Spring WebSocket | Real-time communication support |
| Maven | Dependency and build management |
| Lombok | Boilerplate reduction |
| OpenPDF | PDF document generation |

## 4. Architecture

```text
IoT Device (ESP32)
       |
       v
IoT Communication / Telemetry
       |
       v
FrioVale Backend (Spring Boot)
       |
       +------> Security and Validation
       |
       +------> Application Services
       |
       +------> Data Access (JPA)
       |
       v
MySQL Database
       |
       v
REST Services / WebSocket
       |
       v
FrioVale Frontend
```

The complete architecture must be updated to reflect the actual implemented integrations and deployed services.

## 5. Requirements

- Java Development Kit (JDK) 17.
- Maven or the included Maven Wrapper.
- MySQL Server.
- Git.
- A configured database.
- Environment-specific application settings.

## 6. Installation

Clone the repository:

```bash
git clone https://github.com/FrioVale/Back_FrioVale.git
cd Back_FrioVale
```

### Windows

```bash
mvnw.cmd clean install
```

### Linux / macOS

```bash
chmod +x mvnw
./mvnw clean install
```

## 7. Database configuration

The application uses MySQL and Spring Data JPA.

The current application configuration uses:

- Database name: `frioVale`.
- Default database host: `localhost`.
- Database port: `3306`.
- Time zone: `America/Recife`.

The `MYSQL_HOST` property can override the default database host.

Configure the database credentials locally. Do not commit production passwords.

Example SQL:

```sql
CREATE DATABASE frioVale;
```

The database username and password must be configured according to the local or deployment environment.

Before production deployment:

- Replace default database credentials.
- Configure schema migrations.
- Review the Hibernate DDL strategy.
- Create database indexes.
- Define backup and restoration procedures.

## 8. Application configuration

The current application properties include configuration for:

- Application name.
- Database connection.
- JPA and Hibernate.
- Time zone.
- CORS allowed origins.
- ThingSpeak integration.
- JWT secret and expiration.
- Initial administrator account.

Sensitive values must be externalized through environment variables or a secrets manager.

Never use the default administrator password in production.

The current wildcard CORS configuration must be restricted to trusted frontend origins before deployment.

## 9. Running the application

Start the backend:

```bash
./mvnw spring-boot:run
```

On Windows:

```bash
mvnw.cmd spring-boot:run
```

The default Spring Boot port is 8080 unless overridden by the application configuration.

Local base URL:

```text
http://localhost:8080
```

## 10. Building the application

Create the application package:

```bash
./mvnw clean package
```

Run the generated JAR:

```bash
java -jar target/frioVale-0.0.1-SNAPSHOT.jar
```

The exact generated artifact name may change with the project version.

## 11. API documentation

The technical assessment requires OpenAPI documentation in English.

The API documentation must describe:

- Available endpoints.
- HTTP methods.
- Request parameters.
- Request and response schemas.
- Authentication requirements.
- Authorization rules.
- HTTP status codes.
- Error response formats.
- Example requests and responses.

**Endpoint inventory:** verify the actual controllers in `src/main/java` before publishing endpoint paths. Do not document routes that are not implemented.

Required status-code behavior:

| HTTP status | Meaning |
|---|---|
| 200 | Successful request |
| 201 | Resource created |
| 400 | Invalid request |
| 401 | Authentication required or invalid |
| 403 | Authenticated user lacks permission |
| 404 | Resource not found |
| 500 | Internal server error |

## 12. Security

The project includes Spring Security and OAuth2 Resource Server dependencies.

The final system must demonstrate:

- JWT authentication.
- Authorization by user and device ownership.
- HTTP 401 for unauthenticated requests.
- HTTP 403 for unauthorized access.
- Input validation.
- TLS for external communication.
- Rate limiting.
- Secure secret management.
- OWASP API Security controls.
- LGPD compliance for personal data.

A dependency in the project does not by itself prove that a security requirement is implemented. Each control must be configured and tested.

## 13. Resilience and reliability

The backend must support the following requirements:

| Requirement | Description |
|---|---|
| R1 | Support device reconnection |
| R2 | Receive retransmitted readings |
| R3 | Prevent duplicate records through idempotency |
| R4 | Recover after service restarts |
| R5 | Reject invalid measurements |
| R6 | Handle external service failures |
| R7 | Expose service and device availability |
| R8 | Support health checks, automatic recovery and backup |

Final API requirements include:

- Device and user CRUD.
- Idempotent telemetry ingestion.
- Pagination.
- `/health` endpoint.
- `/predict` integration endpoint.
- Automated tests.

Endpoint implementation and test evidence must be documented.

## 14. Testing

Run the automated test suite:

```bash
./mvnw test
```

The final delivery requires:

- Automated tests for main routes.
- Validation tests.
- Authentication tests.
- Authorization tests.
- Duplicate message tests.
- Failure handling tests.
- At least 60% backend test coverage.
- Static analysis without blocking issues.
- CI execution through GitHub Actions.

## 15. Deployment

The final project must be deployed to a cloud provider and made accessible through a public HTTPS URL.

The deployment documentation must include:

- Cloud provider.
- Public API URL.
- Dockerfile.
- Docker Compose or infrastructure as code.
- Environment configuration.
- Secrets management.
- Automatic deployment pipeline.
- Centralized logs.
- Automatic service restart.
- Database backup.
- Monthly cost estimate.

Localhost execution alone does not satisfy the cloud deployment requirement.

## 16. Repository structure

Current repository structure:

```text
Back_FrioVale/
├── .mvn/
│   └── wrapper/
├── src/
│   └── main/
├── .gitattributes
├── .gitignore
├── mvnw
├── mvnw.cmd
└── pom.xml
```

Recommended structure as the project evolves:

```text
Back_FrioVale/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── pi/
│   │   │           └── friovale/
│   │   └── resources/
│   │       └── application.properties
│   └── test/
├── docs/
│   └── openapi/
├── database/
│   ├── schema.sql
│   ├── seed.sql
│   └── er-diagram.png
├── infra/
│   ├── Dockerfile
│   └── compose.yaml
├── .github/
│   └── workflows/
├── .env.example
├── .gitignore
├── README.md
├── mvnw
├── mvnw.cmd
└── pom.xml
```

This is a target organization. Keep actual source packages and files consistent with the existing application.

## 17. Final delivery checklist

- [ ] Public HTTPS API.
- [ ] Database configured and documented.
- [ ] IoT telemetry ingestion.
- [ ] OpenAPI documentation in English.
- [ ] JWT authentication.
- [ ] Correct 400, 401 and 403 responses.
- [ ] Device and user CRUD.
- [ ] Idempotency.
- [ ] Pagination.
- [ ] `/health`.
- [ ] `/predict`.
- [ ] Automated tests.
- [ ] At least 60% test coverage.
- [ ] Docker deployment.
- [ ] CI/CD pipeline.
- [ ] Centralized logs.
- [ ] Backup and restoration test.
- [ ] Security review.
- [ ] Monthly cloud cost estimate.

## 18. Academic context

This repository is part of the FrioVale Integrative Project, developed for the Internet of Things and Cloud Computing requirements of the academic project.

</details>

<details>
<summary><strong>🇧🇷 Versão em português</strong></summary>

## 1. Sobre o projeto

O FrioVale é uma solução baseada em IoT desenvolvida para monitorar as condições ambientais da cadeia do frio utilizada no armazenamento e transporte de frutas no Vale do São Francisco.

Este repositório contém a aplicação back-end responsável pelos serviços do servidor, persistência de dados, componentes de segurança e recursos de integração da plataforma FrioVale.

O back-end é desenvolvido em Java com Spring Boot.

## 2. Objetivos

- Disponibilizar serviços para a plataforma FrioVale.
- Permitir persistência de dados por meio do MySQL.
- Gerenciar entidades utilizando Spring Data JPA.
- Fornecer infraestrutura de validação e segurança.
- Permitir comunicação entre front-end e back-end.
- Oferecer suporte à integração com fontes de telemetria IoT.
- Criar uma base para monitoramento, consulta e análise de dados ambientais.

## 3. Tecnologias

| Tecnologia | Finalidade |
|---|---|
| Java 17 | Linguagem de programação |
| Spring Boot 3.3.5 | Framework back-end |
| Spring Web | Serviços HTTP |
| Spring Data JPA | Persistência de dados |
| MySQL Connector/J | Conexão com MySQL |
| Spring Security | Infraestrutura de segurança |
| OAuth2 Resource Server | Segurança baseada em tokens |
| Spring Validation | Validação de entradas |
| Spring WebSocket | Suporte à comunicação em tempo real |
| Maven | Gerenciamento de dependências e build |
| Lombok | Redução de código repetitivo |
| OpenPDF | Geração de documentos PDF |

## 4. Arquitetura

```text
Dispositivo IoT (ESP32)
       |
       v
Comunicação IoT / Telemetria
       |
       v
Back-end FrioVale (Spring Boot)
       |
       +------> Segurança e validação
       |
       +------> Serviços da aplicação
       |
       +------> Acesso a dados (JPA)
       |
       v
Banco de dados MySQL
       |
       v
Serviços REST / WebSocket
       |
       v
Front-end FrioVale
```

A arquitetura completa deverá ser atualizada para representar as integrações e serviços efetivamente implementados e implantados.

## 5. Requisitos

- Java Development Kit (JDK) 17.
- Maven ou Maven Wrapper incluído.
- Servidor MySQL.
- Git.
- Banco de dados configurado.
- Configurações da aplicação para cada ambiente.

## 6. Instalação

Clone o repositório:

```bash
git clone https://github.com/FrioVale/Back_FrioVale.git
cd Back_FrioVale
```

### Windows

```bash
mvnw.cmd clean install
```

### Linux / macOS

```bash
chmod +x mvnw
./mvnw clean install
```

## 7. Configuração do banco de dados

A aplicação utiliza MySQL e Spring Data JPA.

A configuração atual utiliza:

- Nome do banco: `frioVale`.
- Host padrão: `localhost`.
- Porta: `3306`.
- Fuso horário: `America/Recife`.

A propriedade `MYSQL_HOST` permite alterar o host padrão.

Configure as credenciais do banco localmente. Não publique senhas de produção.

Exemplo SQL:

```sql
CREATE DATABASE frioVale;
```

O usuário e a senha devem ser configurados de acordo com o ambiente local ou de implantação.

Antes da produção:

- Substitua credenciais padrão.
- Configure migrações do banco.
- Revise a estratégia DDL do Hibernate.
- Crie índices no banco.
- Defina procedimentos de backup e restauração.

## 8. Configuração da aplicação

As propriedades atuais incluem configurações para:

- Nome da aplicação.
- Conexão com o banco.
- JPA e Hibernate.
- Fuso horário.
- Origens CORS permitidas.
- Integração com ThingSpeak.
- Segredo e expiração JWT.
- Conta inicial de administrador.

Valores sensíveis devem ser externalizados por variáveis de ambiente ou gerenciador de segredos.

Nunca utilize a senha padrão do administrador em produção.

A configuração CORS com curinga deverá ser restringida às origens confiáveis do front-end antes da implantação.

## 9. Execução da aplicação

Inicie o back-end:

```bash
./mvnw spring-boot:run
```

No Windows:

```bash
mvnw.cmd spring-boot:run
```

A porta padrão do Spring Boot é 8080, salvo alteração na configuração.

URL local:

```text
http://localhost:8080
```

## 10. Compilação da aplicação

Gere o pacote da aplicação:

```bash
./mvnw clean package
```

Execute o JAR gerado:

```bash
java -jar target/frioVale-0.0.1-SNAPSHOT.jar
```

O nome exato do artefato poderá mudar conforme a versão do projeto.

## 11. Documentação da API

A avaliação técnica exige documentação OpenAPI em inglês.

A documentação deverá descrever:

- Endpoints disponíveis.
- Métodos HTTP.
- Parâmetros de requisição.
- Estruturas de requisição e resposta.
- Requisitos de autenticação.
- Regras de autorização.
- Códigos HTTP.
- Formatos de erros.
- Exemplos de requisições e respostas.

**Inventário de endpoints:** confira os controllers reais em `src/main/java` antes de publicar os caminhos da API. Não documente rotas que não estejam implementadas.

Comportamento obrigatório dos códigos HTTP:

| Código HTTP | Significado |
|---|---|
| 200 | Requisição realizada com sucesso |
| 201 | Recurso criado |
| 400 | Requisição inválida |
| 401 | Autenticação necessária ou inválida |
| 403 | Usuário autenticado sem permissão |
| 404 | Recurso não encontrado |
| 500 | Erro interno do servidor |

## 12. Segurança

O projeto possui dependências do Spring Security e OAuth2 Resource Server.

O sistema final deverá demonstrar:

- Autenticação JWT.
- Autorização por usuário e propriedade do dispositivo.
- HTTP 401 para requisições não autenticadas.
- HTTP 403 para acessos não autorizados.
- Validação de entradas.
- TLS nas comunicações externas.
- Limitação de requisições.
- Gerenciamento seguro de segredos.
- Controles OWASP API Security.
- Conformidade com a LGPD para dados pessoais.

A existência de uma dependência não comprova, por si só, que um requisito de segurança foi implementado. Cada controle precisa ser configurado e testado.

## 13. Resiliência e confiabilidade

O back-end deverá atender aos seguintes requisitos:

| Requisito | Descrição |
|---|---|
| R1 | Suporte à reconexão dos dispositivos |
| R2 | Recebimento de leituras reenviadas |
| R3 | Prevenção de registros duplicados por idempotência |
| R4 | Recuperação após reinicialização dos serviços |
| R5 | Rejeição de medições inválidas |
| R6 | Tratamento de falhas em serviços externos |
| R7 | Disponibilidade dos serviços e dispositivos |
| R8 | Health checks, recuperação automática e backup |

Requisitos finais da API:

- CRUD de dispositivos e usuários.
- Ingestão de telemetria com idempotência.
- Paginação.
- Endpoint `/health`.
- Endpoint de integração `/predict`.
- Testes automatizados.

A implementação e as evidências dos endpoints deverão ser documentadas.

## 14. Testes

Execute a suíte de testes:

```bash
./mvnw test
```

A entrega final exige:

- Testes automatizados das principais rotas.
- Testes de validação.
- Testes de autenticação.
- Testes de autorização.
- Testes de mensagens duplicadas.
- Testes de tratamento de falhas.
- Cobertura mínima de 60% no back-end.
- Análise estática sem problemas bloqueadores.
- Execução de CI pelo GitHub Actions.

## 15. Implantação

O projeto final deverá estar implantado em um provedor de nuvem e acessível por uma URL pública HTTPS.

A documentação de implantação deverá incluir:

- Provedor de nuvem.
- URL pública da API.
- Dockerfile.
- Docker Compose ou infraestrutura como código.
- Configuração do ambiente.
- Gerenciamento de segredos.
- Pipeline de implantação automática.
- Logs centralizados.
- Reinicialização automática dos serviços.
- Backup do banco.
- Estimativa de custo mensal.

Executar apenas em localhost não atende ao requisito de Cloud Computing.

## 16. Estrutura do repositório

Estrutura atual:

```text
Back_FrioVale/
├── .mvn/
│   └── wrapper/
├── src/
│   └── main/
├── .gitattributes
├── .gitignore
├── mvnw
├── mvnw.cmd
└── pom.xml
```

Estrutura recomendada para evolução:

```text
Back_FrioVale/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── pi/
│   │   │           └── friovale/
│   │   └── resources/
│   │       └── application.properties
│   └── test/
├── docs/
│   └── openapi/
├── database/
│   ├── schema.sql
│   ├── seed.sql
│   └── diagrama-er.png
├── infra/
│   ├── Dockerfile
│   └── compose.yaml
├── .github/
│   └── workflows/
├── .env.example
├── .gitignore
├── README.md
├── mvnw
├── mvnw.cmd
└── pom.xml
```

Esta é uma organização de destino. Os pacotes e arquivos reais devem permanecer compatíveis com a aplicação existente.

## 17. Checklist da entrega final

- [ ] API pública HTTPS.
- [ ] Banco configurado e documentado.
- [ ] Recebimento de telemetria IoT.
- [ ] Documentação OpenAPI em inglês.
- [ ] Autenticação JWT.
- [ ] Respostas corretas 400, 401 e 403.
- [ ] CRUD de dispositivos e usuários.
- [ ] Idempotência.
- [ ] Paginação.
- [ ] `/health`.
- [ ] `/predict`.
- [ ] Testes automatizados.
- [ ] Cobertura mínima de 60%.
- [ ] Implantação Docker.
- [ ] Pipeline CI/CD.
- [ ] Logs centralizados.
- [ ] Teste de backup e restauração.
- [ ] Revisão de segurança.
- [ ] Estimativa de custo mensal da nuvem.

## 18. Contexto acadêmico

Este repositório faz parte do Projeto Integrador FrioVale, desenvolvido para atender aos requisitos de Internet das Coisas e Cloud Computing do projeto acadêmico.

</details>
