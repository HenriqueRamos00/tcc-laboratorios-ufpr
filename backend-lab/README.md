# Backend Lab

Backend monolítico do Portal Interno do TCC - Laboratórios UFPR. O projeto fornece a API que será consumida pelo Portal Interno e utiliza Java 21, Spring Boot, Spring Web, Spring Data JPA e PostgreSQL.

Os comandos deste documento devem ser executados a partir da pasta `backend-lab`.

## Requisitos

- Java 21;
- Docker Engine e Docker Compose, para a execução conteinerizada;
- ou Maven Wrapper (`./mvnw`) e uma instância local do PostgreSQL.

Confira a versão do Java antes de executar:

```bash
java -version
```

## Arquitetura

O backend é um monólito Spring organizado por responsabilidade, seguindo o fluxo:

```text
Controller REST -> Use Case -> Repository -> PostgreSQL
```

```text
src/main/java/br/ufpr/tcc/backend_lab/
├── application/
│   ├── dto/request/
│   ├── dto/response/
│   └── usecase/
├── domain/
│   ├── exception/
│   ├── model/entity/
│   └── repository/
├── infrastructure/
│   ├── config/
│   └── security/
└── presentation/
    ├── advice/
    └── rest/
```

- `application`: DTOs e casos de uso. Cada operação relevante deve ter seu próprio Use Case;
- `domain`: entidades JPA, exceções e interfaces de repository;
- `infrastructure`: configurações técnicas e integrações externas;
- `presentation`: controllers REST e tratamento global de erros.

Controllers devem permanecer pequenos e regras de negócio devem ficar nos Use Cases. Entidades JPA não devem ser retornadas diretamente pela API.

## Execução com Docker

O Compose inicia a API e o PostgreSQL. Para personalizar portas ou credenciais, copie o arquivo de exemplo:

```bash
cp .env.example .env
```

Suba os serviços:

```bash
docker compose up --build
```

Para executar em segundo plano:

```bash
docker compose up --build -d
```

Verifique o estado dos serviços e os logs:

```bash
docker compose ps
docker compose logs -f backend-lab
```

Finalize os containers sem remover os dados do PostgreSQL:

```bash
docker compose down
```

A API ficará disponível em `http://localhost:8080`. O Compose aguarda o PostgreSQL ficar saudável antes de iniciar o backend e monitora a API pelo endpoint `/health`.

## API disponível

### Health check

```http
GET /health
```

Resposta:

```json
{
  "status": "UP"
}
```

Também é possível consultar diretamente:

```bash
curl http://localhost:8080/health
```

### Login interno

Para entrar no portal interno, envie as credenciais para:

```http
POST /api/auth/internal/login
Content-Type: application/json
```

O endpoint `POST /auth/login` também está disponível como atalho compatível com a primeira versão do contrato.

Exemplo de requisição:

```json
{
  "email": "admin@exemplo.com",
  "password": "senha"
}
```

Quando as credenciais forem válidas, a API retorna o token e os dados básicos do usuário:

```json
{
  "accessToken": "<jwt>",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "user": {
    "id": 1,
    "name": "Administrador",
    "email": "admin@exemplo.com",
    "role": "ADMIN"
  }
}
```

Envie o token nas demais requisições protegidas:

```http
Authorization: Bearer <jwt>
```

Para criar um administrador ou técnico na primeira execução local, preencha as variáveis `AUTH_SEED_ADMIN_*` e/ou `AUTH_SEED_TECHNICIAN_*` no arquivo `.env`. As senhas são armazenadas com BCrypt. Esse recurso serve apenas para preparar o ambiente; o cadastro completo de usuários será implementado posteriormente.

## Execução local

Inicie um PostgreSQL local com um banco chamado `backend_lab` e configure as variáveis de conexão. Em seguida, execute:

```bash
./mvnw spring-boot:run
```

As principais variáveis da aplicação são:

| Variável | Padrão | Uso |
| --- | --- | --- |
| `SERVER_PORT` | `8080` | Porta HTTP da API |
| `DB_URL` | `jdbc:postgresql://localhost:5432/backend_lab` | URL JDBC do PostgreSQL |
| `DB_USERNAME` | `backend_lab` | Usuário do banco |
| `DB_PASSWORD` | `backend_lab` | Senha do banco |
| `JPA_DDL_AUTO` | `update` | Estratégia de schema do Hibernate |
| `JWT_SECRET` | valor de desenvolvimento | Chave de assinatura do JWT; altere em ambientes reais |
| `JWT_EXPIRATION_SECONDS` | `3600` | Validade do JWT em segundos |

O arquivo `.env` é local e não deve conter credenciais reais versionadas. Use `.env.example` como referência.

## Testes

Execute todos os testes com:

```bash
./mvnw test
```

Os testes usam H2 em memória e não exigem um PostgreSQL ativo.

## Build do artefato

Para gerar o JAR executável:

```bash
./mvnw clean package
```

O artefato será criado em `target/backend-lab-<versão>.jar` e pode ser iniciado com:

```bash
java -jar target/backend-lab-<versão>.jar
```
