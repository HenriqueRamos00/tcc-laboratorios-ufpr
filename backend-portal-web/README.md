# PortalHexagonalApi

API em ASP.NET Core organizada em camadas seguindo a ideia de arquitetura hexagonal, com `Domain`, `Application`, `Adapters` e `Api`.

## Dependencias

- .NET SDK 10.0 ou superior
- Git
- Um terminal com acesso ao comando `dotnet`

Para conferir a versao instalada:

```bash
dotnet --version
```

O projeto usa `net10.0`, entao versoes anteriores do SDK nao devem compilar a solucao.

## Estrutura

```text
PortalHexagonalApi/
  Portal.Api/          # Entrada HTTP e configuracao de DI
  Portal.Application/  # Casos de uso e portas
  Portal.Domain/       # Entidades de dominio
  Portal.Adapters/     # Implementacoes externas/fakes das portas
```

## Como restaurar e compilar

Na raiz do repositorio:

```bash
dotnet restore PortalHexagonalApi/PortalHexagonalApi.slnx
dotnet build PortalHexagonalApi/PortalHexagonalApi.slnx
```

## Como rodar a API

Execute o projeto `Portal.Api`:

```bash
dotnet run --project PortalHexagonalApi/Portal.Api
```

Em ambiente de desenvolvimento, a API sobe em:

```text
http://localhost:5125
```

A documentacao OpenAPI fica disponivel em:

```text
http://localhost:5125/openapi/v1.json
```

## Endpoints disponiveis

```http
GET http://localhost:5125/api/quotes
GET http://localhost:5125/api/quotes/{id}
GET http://localhost:5125/api/lab-results
GET http://localhost:5125/api/lab-results/{id}
POST http://localhost:5125/api/salesforce/token
POST http://localhost:5125/api/auth/internal/login
GET  http://localhost:5125/api/technicians
POST http://localhost:5125/api/technicians
PUT  http://localhost:5125/api/technicians/{id}
PATCH http://localhost:5125/api/technicians/{id}/status
```

As rotas internas de técnicos exigem um JWT interno com perfil `ADMIN`. O login é anônimo e encaminha as credenciais ao Backend Lab, que emite o token. Nas demais rotas, o C# valida o token e encaminha o Bearer original ao Java para que o usuário ativo e o perfil atual também sejam conferidos no PostgreSQL. O endereço do Java é uma configuração de saída do C#; os frontends usam somente `/api` na origem do portal.

Exemplos:

```bash
curl http://localhost:5125/api/quotes
curl http://localhost:5125/api/quotes/quote-1
curl http://localhost:5125/api/lab-results
curl http://localhost:5125/api/lab-results/result-1
curl -X POST http://localhost:5125/api/salesforce/token
```

## Configuracao do Salesforce

A API le configuracoes do Salesforce pela secao `Salesforce`, que pode vir do
`appsettings`, de variaveis de ambiente ou do arquivo `.env` usado pelo Docker
Compose.

```env
Salesforce__Url=https://login.salesforce.com
Salesforce__TokenPath=/services/oauth2/token
Salesforce__ApiVersion=v60.0
Salesforce__GrantType=client_credentials
Salesforce__ClientId=your-connected-app-client-id
Salesforce__ClientSecret=your-connected-app-client-secret
```

O endpoint `POST /api/salesforce/token` chama o OAuth do Salesforce e retorna o
token recebido.

## Integração com o Backend Lab

Em execução local, o destino padrão é `BackendLab__BaseUrl=http://localhost:8080`.
No Compose da raiz, a URL é `http://backend-lab:8080`. Em Compose independente
do C#, use `BACKEND_LAB_DOCKER_BASE_URL=http://host.docker.internal:8080`.
`BackendLab__TimeoutSeconds` define o limite da chamada; o padrão é 10 segundos
e o valor aceito vai de 1 a 60 segundos.
Em produção, configure `BackendLab__BaseUrl` com HTTPS.

O C# e o Java precisam receber os mesmos valores de chave, emissor, audiência e
tolerância de relógio do JWT interno. O `.env` da raiz é a origem única desses
valores no Compose integrado: `JWT_SECRET`, `JWT_ISSUER`, `JWT_AUDIENCE` e
`JWT_CLOCK_SKEW_SECONDS`. Em execução local independente, informe valores iguais
a ambos. Os padrões de emissor e audiência são `tcc-backend-lab` e
`tcc-portal-api-internal`; a tolerância padrão é 30 segundos e aceita até 60.
O Java emite tokens válidos por 3600 segundos por padrão, ajustável por
`JWT_EXPIRATION_SECONDS`. Use um segredo próprio fora do desenvolvimento. A
falha do Java afeta somente as rotas internas dependentes dele e não impede a
API C# de iniciar.
Tokens emitidos antes do alinhamento de HS256, emissor e audiência exigem novo
login depois da atualização coordenada dos serviços.

Nas requisições de técnicos, o C# confere a presença dos campos obrigatórios e
mantém a resposta pública `{ "message": "..." }`. O Java é a autoridade para
normalizar nome, e-mail, unidade e especialidade, validar formato e limites dos
campos, verificar e-mails duplicados e validar a senha inicial (presença e
limite de 72 caracteres). A senha não tem espaços removidos; na edição, omitir
a senha mantém a senha atual.

## Como rodar com Docker

Crie um arquivo `.env` a partir do exemplo e suba o Compose:

```bash
cp .env.example .env
docker compose up --build
```

Por padrao, a API fica disponivel em:

```text
http://localhost:5125
```
