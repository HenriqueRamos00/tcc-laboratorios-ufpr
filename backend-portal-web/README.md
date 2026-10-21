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

Na pasta `backend-portal-web/`:

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
POST http://localhost:5125/auth/login
GET http://localhost:5125/auth/me
POST http://localhost:5125/api/salesforce/token
```

Exemplos:

```bash
curl http://localhost:5125/api/quotes
curl http://localhost:5125/api/quotes/quote-1
curl http://localhost:5125/api/lab-results
curl http://localhost:5125/api/lab-results/result-1
curl -X POST http://localhost:5125/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"cliente@exemplo.com","password":"senha"}'
curl -X POST http://localhost:5125/api/salesforce/token
```

### Autenticação do Portal do Cliente

Há duas fontes, selecionadas exclusivamente por `Portal__DataSource` na inicialização:

| Fonte | Autenticação | Sessão do navegador |
| --- | --- | --- |
| `backend-lab` | `BackendLab__BaseUrl` → Spring Backend Lab | Token Spring preservado pelo C#; comportamento existente das demais rotas mantido |
| `lactec` | `BackendAuth__BaseUrl` → Spring Backend Auth independente | JWT HS256 emitido e validado pela API C# |

`lactec` é o modo Backend Auth. Não existe terceira fonte `backend-auth`, seleção por
requisição ou fallback entre os serviços. Valores ausentes e desconhecidos impedem
a inicialização. As configurações da fonte inativa são ignoradas. Orçamentos,
documentos, equipamentos e ensaios mantêm suas implementações atuais; a seleção de
`LabResults:Source` e a lógica atual de dados/tenant continuam independentes.

`POST /auth/login` é público e recebe `{ "email": "...", "password": "..." }`.
O controller chama `ILoginUseCase` → `LoginService` → `IClientAuthenticator`.
No modo `lactec`, `BackendAuthAdapter` envia a senha intacta e o email normalizado
para `/api/auth/login`, com o Bearer de serviço configurado **somente no C#**.
Não há repetição automática nem redirecionamento; o timeout é de 10 segundos.
A API recusa identidades que não sejam `CLIENTE`, tenham outro `serviceCode` ou
estejam incompletas. Administradores do Backend Auth administram apenas esse serviço
e não recebem acesso de administrador ao portal.

Exemplo da resposta em `lactec` (o campo `Token` mantém a inicial maiúscula):

```json
{
  "Token": "<JWT do portal>",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {
    "id": "42",
    "name": "Marina Costa",
    "email": "marina@empresa.com",
    "role": "CLIENTE",
    "serviceCode": "portal-web",
    "authorizations": { "companyCnpjs": ["04252011000110", "12ABC34501DE35"] }
  }
}
```

O JWT contém `iss`, `aud`, `sub` (ID), `iat`, `nbf`, `exp`, `jti`, `nome`, `email`,
`roles=CLIENTE`, `authSource=lactec`, `serviceCode` e `companyCnpjs` como array,
inclusive vazio. Não contém senha, hash, token de serviço ou IDs/nomes de empresas
inventados. O formato antigo de `user.companies` e o token Spring permanecem
inalterados em `backend-lab`.

No modo `lactec`, todas as rotas `/api/**` e `GET /auth/me` exigem
`Authorization: Bearer <JWT do portal>`. O C# verifica assinatura HS256, emissor,
audiência, validade sem tolerância extra, identidade, fonte, serviço e papel.
`GET /auth/me` devolve o mesmo `user` usando os claims, sem consultar o Backend Auth.
Login, OpenAPI e preflight CORS são públicos. No modo `backend-lab`, `/auth/me`
retorna 501 e as demais rotas conservam a autorização anterior. O OpenAPI descreve
o Bearer quando `lactec` está ativo.

A sessão dura 900 segundos por padrão. Alterações de senha, empresas, estado da
conta, serviço ou token de serviço só afetam JWTs já emitidos quando expiram.
Não há refresh token, revogação remota do JWT ou logout no servidor. Logout no
Angular remove token, perfil e papel do armazenamento local. Uma nova autenticação
é necessária após expiração; trocar a chave de assinatura invalida todas as sessões.
Os CNPJs são informações de autorização para a futura integração de dados: esta
mudança **não implementa filtragem de orçamentos ou ensaios por empresa**.

O Angular guarda o JWT em `localStorage` (`lactec.token`) e envia Bearer apenas às
rotas protegidas da origem/caminho configurados em `API_URL`. Nome e papel vêm de
`user`; ao recarregar uma sessão `lactec`, `/auth/me` restaura a identidade. A
restauração do Backend Lab usa o seu perfil/claim `role`. O login de desenvolvimento
foi removido. Credenciais erradas não encerram outra sessão, 401 de uma rota
protegida encerra a sessão e 403 é exibido sem logout automático. Erros 401/403 não
são substituídos por orçamentos de demonstração.

| HTTP | Significado no login |
| --- | --- |
| 400 | Dados inválidos |
| 401 | Credenciais inválidas, conta inativa ou cliente de outro serviço |
| 429 | Cinco falhas do cliente; `Retry-After` em segundos, cooldown padrão de 15 minutos |
| 502 | Origem indisponível, timeout, identidade inválida ou token de integração recusado |

O cooldown pertence à conta no Backend Auth, não ao IP do C#. Angular mostra a
espera e desabilita novos envios sem tentar novamente sozinho. CORS expõe
`Retry-After`. Login e `/auth/me` usam `Cache-Control: no-store`; erros usam
ProblemDetails sem expor respostas internas. Senhas antigas continuam aceitas no
login; a política de composição é aplicada ao cadastro no Backend Auth.

### Configuração e execução em lactec

1. Suba o serviço independente seguindo o [README do Backend Auth](../backend-auth/README.md),
   cadastre o primeiro administrador pelo link dos logs e entre no Swagger UI.
2. Crie o serviço com código `portal-web`, gere um token de serviço e crie um
   cliente ativo vinculado a esse serviço e, opcionalmente, a empresas.
3. Preencha o `.env` da raiz com `Portal__DataSource=lactec`,
   `BACKEND_AUTH_SERVICE_TOKEN` (o `accessToken` começando em `svc_`) e uma chave
   exclusiva `PORTAL_JWT_SIGNING_KEY`, gerada com `openssl rand -base64 32`.
4. Execute da raiz, com o Backend Auth já iniciado:

```bash
docker compose -f compose.yaml -f compose.auth.yaml up -d --build frontend
```

Esse comando inicia somente o Portal do Cliente e a API C#; não inicia o Backend
Lab nem o PostgreSQL. Pare os serviços Backend Lab/Portal Interno que tiver iniciado
se estiver alternando o ambiente completo. O Compose original permanece disponível
para o modo `backend-lab`.

O overlay conecta `portal-api` à rede externa `backend-auth_default`, configurável
em `BACKEND_AUTH_NETWORK`. O endereço interno padrão é `http://backend-auth:8080`,
evita depender da porta de loopback do host e não publica o SQL Server em outra
interface. Para usar o Compose isolado do C# a partir da raiz:

```bash
docker compose --env-file .env -f backend-portal-web/docker-compose.yml -f compose.auth.yaml up -d --build portal-api
```

Para `dotnet run` fora do Docker, exporte as variáveis do processo:
`Portal__DataSource=lactec`, `BackendAuth__BaseUrl=http://localhost:8081`,
`BackendAuth__ServiceCode=portal-web`, `BackendAuth__ServiceToken`,
`PortalJwt__Issuer`, `PortalJwt__Audience` e `PortalJwt__SigningKey`.
`PortalJwt__ExpirationSeconds` é opcional (900) e deve ser positivo. A chave deve
ser Base64 de pelo menos 32 bytes aleatórios, diferente de chaves do Backend Lab.
Não há credenciais ou segredo padrão. Configuração inválida impede o startup.
O `.env` do C# é carregado pelo Compose, não pelo `dotnet run`.

Em `backend-lab`, configure somente `Portal__DataSource=backend-lab` e
`BackendLab__BaseUrl` (no Compose da raiz, `BACKEND_LAB_BASE_URL`). Por exemplo,
`http://localhost:8080` fora do Docker ou `http://backend-lab:8080` no Compose integrado.

Em implantação, use HTTPS entre navegador e C# e entre C# e Backend Auth, restrinja
o Backend Auth à rede dos servidores e mantenha token de serviço/chave JWT fora do
frontend e do Git. O JWT em localStorage pode ser lido por JavaScript da página;
mantenha a política de conteúdo e a proteção contra XSS na publicação. As URLs HTTP
dos exemplos servem à execução local.

### Validação

```bash
# Em backend-portal-web/
dotnet test PortalHexagonalApi/Portal.Tests/Portal.Tests.csproj
dotnet publish PortalHexagonalApi/Portal.Api/Portal.Api.csproj -c Release
# Em frontend/, com Node 24+ e Chromium
npm ci
npm test -- --watch=false
npm run build
```

Os testes HTTP cobrem as duas fontes, claims, expiração, assinatura, escopo do
serviço, `/auth/me`, falhas de integração, cooldown, CORS e OpenAPI. O frontend
cobre armazenamento/restauração, envio restrito do Bearer, logout, mensagens e
espera do login. Para um smoke real, mantenha o Backend Lab parado, use o Backend
Auth com SQL Server, autentique pelo C#, consulte `/auth/me` e `/api/quotes` com o
JWT e confirme 401 sem JWT e 429 após cinco senhas incorretas. Para verificar
expiração sem esperar 15 minutos, reduza `PORTAL_JWT_EXPIRATION_SECONDS` no ambiente
de teste e recrie a API.

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
