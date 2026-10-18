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
POST http://localhost:5125/auth/login
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

### Login de cliente

`POST /auth/login` chama a porta de entrada `ILoginUseCase`, implementada por
`LoginService`. O caso de uso acessa a porta de saída `IClientAuthenticator`,
cujo adapter Spring encaminha `email` e `password` para `POST /api/auth/login` no Backend Lab
Spring. O Spring autentica clientes ativos e emite o token. A API C# preserva
esse token, sem reemiti-lo nem validar seu JWT, e responde com `Token`,
`tokenType`, `expiresIn` e `user` (`id`, `name`, `email`, `role` e
`companies`).

Requisição inválida retorna 400, credenciais inválidas retornam 401 e falhas
de conexão, timeout ou resposta JSON inválida da origem retornam 502 em
ProblemDetails sem detalhes internos. Esta rota adapta somente o login; ela
não autoriza os demais endpoints da API. Com a fonte `lactec`, a rota responde
501 com o código `authentication-not-available`, pois o contrato de autenticação
Lactec ainda está pendente. Não há fallback automático entre fontes.

### Fonte de dados do portal

Configure `Portal__DataSource=backend-lab` para selecionar o login Spring ou
`Portal__DataSource=lactec` para preservar as integrações atuais.
O registro das portas fica centralizado em `PortalSourceRegistration`, seguindo
o fluxo controller -> caso de uso -> porta -> adapter. A seleção controla o
login; orçamentos, documentos e ensaios mantêm suas implementações atuais até
a integração das respectivas portas. `LabResults:Source` continua configurando
a fonte de ensaios.

O modo `lactec` inicia sem endereço ou conexão com o Spring e usa
`UnavailableClientAuthenticator` na porta de autenticação. O login de
desenvolvimento existente no frontend permanece disponível; o login pela API
C# retorna 501 até a implementação do contrato Lactec. As demais portas mantêm
suas implementações atuais nos dois modos. Valores desconhecidos ou vazios são
recusados. `Portal__DataSource` é obrigatório no ambiente;
não há fonte padrão no `appsettings` ou nos Composes. A escolha da fonte é
feita na inicialização; reinicie a API após alterá-la.

No Compose integrado, defina a variável no `.env` da raiz do repositório.
No Compose isolado, use o `.env` de `backend-portal-web/`. Para `dotnet run`,
configure a variável no ambiente do processo.

`BackendLab__BaseUrl` configura o endereço obrigatório quando a fonte é
`backend-lab`. Trocar esse endereço não seleciona outro adapter. Não há URL
padrão na classe de opções, no `appsettings` ou nos Composes; um endereço ausente
ou inválido impede a inicialização da API com a fonte `backend-lab`.

Para executar `dotnet run` fora do Docker, configure `BackendLab__BaseUrl` no
ambiente do processo, por exemplo `http://localhost:8080`; o .NET não carrega o
arquivo `.env` automaticamente. No Compose integrado, preencha
`BACKEND_LAB_BASE_URL` no `.env` da raiz; no Compose isolado, preencha
`BackendLab__BaseUrl` no `.env` de `backend-portal-web/`. Os `.env.example`
mostram os endereços adequados a cada rede. Para o container isolado alcançar o
host, o Backend Lab precisa aceitar conexões pela interface publicada.

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
