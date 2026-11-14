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
GET http://localhost:5125/api/quotes?page=1&pageSize=50
GET http://localhost:5125/api/quotes/{id}
GET http://localhost:5125/api/quotes/{id}/document
GET http://localhost:5125/api/quotes/{id}/reports
GET http://localhost:5125/api/lab-results
GET http://localhost:5125/api/lab-results/{id}
POST http://localhost:5125/auth/login
GET http://localhost:5125/auth/me
```

Exemplos:

```bash
curl -i 'http://localhost:5125/api/quotes?page=2&pageSize=20' -H 'Authorization: Bearer <JWT>'
curl http://localhost:5125/api/quotes/quote-1 -H 'Authorization: Bearer <JWT>'
curl http://localhost:5125/api/lab-results
curl http://localhost:5125/api/lab-results/result-1
curl -X POST http://localhost:5125/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"cliente@exemplo.com","password":"senha"}'
```

### Autenticação do Portal do Cliente

Há duas fontes, selecionadas exclusivamente por `Portal__DataSource` na inicialização:

| Fonte | Autenticação | Sessão do navegador |
| --- | --- | --- |
| `backend-lab` | `BackendLab__BaseUrl` → Spring Backend Lab | Token Spring preservado; orçamentos e documentos consultados pelos adapters C# |
| `lactec` | `BackendAuth__BaseUrl` → Spring Backend Auth independente | JWT HS256 emitido e validado pela API C# |

`lactec` é o modo Backend Auth. Não existe terceira fonte `backend-auth`, seleção por
requisição ou fallback entre os serviços. Valores ausentes e desconhecidos impedem
a inicialização. As configurações da fonte inativa são ignoradas. No modo `lactec`,
os orçamentos são consultados no Salesforce com os CNPJs autorizados no JWT.
Com `Portal__UseDemoData=true`, o C# usa `FakeSalesforceQuoteRepository` para os
orçamentos. A opção não afeta equipamentos, ensaios nem relatórios no modo Auth, que vêm do
AutoLAB com o escopo de `LabResults__Scope` (ver [Escopo de visibilidade](#escopo-de-visibilidade)).

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
retorna 501 e as consultas de orçamento e relatórios encaminham o Bearer Spring à origem.
No Compose regular, equipamentos e indicadores retornam 501 (`lab-integration-pending`)
até a implementação dos endpoints de cliente no Spring. O OpenAPI descreve o Bearer quando `lactec`
está ativo.

No modo `backend-lab`, as rotas C# `/api/quotes`, `/api/quotes/{id}` e
`/api/quotes/{id}/document` adaptam, respectivamente, `GET /api/client/quotes` e suas
rotas de detalhe e documento no Spring. As três exigem um Bearer sintaticamente válido
antes de executar a consulta. Cada chamada encaminha somente o Bearer da requisição
atual; o C# não interpreta claims desse token. O Spring verifica perfil,
cliente, empresa e vínculos ativos a cada leitura. IDs continuam strings nos contratos
públicos; a origem Spring usa identificadores numéricos positivos. `opportunityId` é
uma string vazia quando não há equivalente na origem, os contatos nulos são expostos
como strings vazias. `stage` é um texto obrigatório e não vazio preparado pelo Spring;
resposta sem etapa válida no contrato de transporte resulta em 502, sem inventar progresso.

No modo `backend-lab`, a origem Spring fornece o PDF anexado ou gera a representação
do orçamento quando não há anexo. O C# apenas transporta os bytes. Falha de origem,
timeout ou resposta inválida retorna 502; 401, 403 e orçamento inexistente permanecem
distintos. Falhas nessa fonte não consultam Salesforce, fixtures ou PDFs gerados.
Lactec mantém Salesforce, documentos locais e geração de proposta existentes.

### Relatórios nas duas composições

`GET /api/reports` devolve a lista paginada em array, com `X-Total-Count`, `X-Page`
e `X-Page-Size`. O contrato inclui os metadados AutoLAB e mantém os campos de relatórios
publicados usados pelos clientes Spring. `GET /api/quotes/{id}/reports` consulta o orçamento
autorizado, devolve a conclusão e seus relatórios, ou 204 quando não há laudos.
O frontend utiliza o ID do orçamento nessa consulta, preservando IDs próprios de cada fonte.

Em `compose.yaml`, o adapter Spring consulta `/api/client/reports`, os relatórios por
orçamento e `/api/client/reports/{id}/file`. `ReportDocuments__Source=not-connected`
desliga apenas o provider AutoLAB; os relatórios publicados Spring permanecem disponíveis.
Em `compose.auth.yaml`, `ReportDocuments__Source=sql-server` seleciona os relatórios AutoLAB.
Não há fallback entre as duas origens.

As rotas `/api/reports/{id}/document` e `/api/reports/{id}/file` devolvem o mesmo documento
da fonte selecionada. IDs Spring são strings; IDs AutoLAB são GUIDs codificados.
`pdfAvailable` informa se o documento pode ser baixado; `hasSignedDocument` identifica
PDF assinado do AutoLAB e não é usado para declarar assinatura digital em documentos Spring.
O download mantém o PDF persistido, o nome do arquivo e `Cache-Control: no-store`.

`LabResults__Source=not-connected` registra explicitamente a integração laboratorial
pendente, sem validar conexão SQL. `LaboratoryProgress__Source=not-connected` mantém a
etapa enviada pelo Spring. Os detalhes das adições futuras estão no
[plano do Backend Lab](../backend-lab/docs/integracao-portal-cliente.md).


A sessão dura 900 segundos por padrão. Alterações de senha, empresas, estado da
conta, serviço ou token de serviço só afetam JWTs já emitidos quando expiram.
Não há refresh token, revogação remota do JWT ou logout no servidor. Logout no
Angular remove token, perfil e papel do armazenamento local. Uma nova autenticação
é necessária após expiração; trocar a chave de assinatura invalida todas as sessões.
Os CNPJs do JWT autorizam as consultas de orçamentos do Salesforce. A filtragem
dos ensaios por empresa está disponível para as duas empresas do demo; a conexão
com o AutoLAB real permanece fora desta integração.

O Angular guarda o JWT em `localStorage` quando **Me mantenha conectado** está
marcado, ou em `sessionStorage` quando desmarcado. Nome, identidade e papel usam o
mesmo armazenamento; logout limpa ambos. A escolha vale para as duas fontes e não
estende a validade do JWT. O frontend valida o formato da resposta, validade e
perfil de cliente antes de persistir uma sessão; assinatura e autorização são
verificadas pelo backend. Angular envia Bearer apenas às
rotas protegidas da origem/caminho configurados em `API_URL`. Nome e papel vêm de
`user`; ao recarregar uma sessão `lactec`, `/auth/me` restaura a identidade. A
restauração do Backend Lab usa o seu perfil/claim `role`. O login de desenvolvimento
foi removido. Credenciais erradas não encerram outra sessão, 401 de uma rota
protegida encerra a sessão e 403 é exibido sem logout automático. Erros da API não
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

### Orçamentos do Salesforce por CNPJ

No modo `lactec`, `IQuoteRepository` usa `SalesforceQuoteRepository`. O adapter recebe
o escopo pela porta `IQuoteCompanyScope`, implementada no HTTP por
`AuthenticatedQuoteCompanyScope`, que lê somente `companyCnpjs` do JWT validado.
O cliente não pode ampliar esse escopo com parâmetros, cabeçalhos ou IDs de empresas.

Uma empresa aplica `WHERE Opportunity.Account.CNPJ__c = '76436849000174'` em todas as
consultas. A lista é paginada no próprio Salesforce: uma contagem e uma página, como em
`GET /api/quotes?page=3&pageSize=20`:

```sql
SELECT COUNT() FROM Quote WHERE Opportunity.Account.CNPJ__c = '76436849000174'

SELECT Id, Name, Codigo_da_Cotacao__c, Status, OpportunityId, Opportunity.Codigo_da_Oportunidade__c, ...
FROM Quote
WHERE Opportunity.Account.CNPJ__c = '76436849000174'
ORDER BY CreatedDate DESC, Id DESC LIMIT 20 OFFSET 40
```

Várias empresas usam `IN ('04252011000110', '76436849000174')`, sem duplicatas, nas
duas consultas. O formato dos CNPJs é canônico, com 14 caracteres, incluindo
suporte a CNPJ alfanumérico. O número do exemplo não é fixado no código.

`page` começa em 1 e `pageSize` vale 50 por padrão (máximo 200), como nas demais listas
(ver [Paginacao](#paginacao)); valor inválido responde 400 sem consultar o Salesforce. O
corpo é o array da página e o total vem em `X-Total-Count`, `X-Page` e `X-Page-Size`.
A contagem dá o total; página além dele não faz a segunda consulta. `Id DESC` desempata
orçamentos criados no mesmo instante. O SOQL recusa `OFFSET` acima de 2000: para páginas
que começam depois disso, a consulta é feita com `LIMIT offset + pageSize`, sem `OFFSET`,
e a API segue o cursor `nextRecordsUrl` do Salesforce até a página pedida (só caminhos
relativos de `/services/data/` são seguidos). É mais caro, mas só acontece para quem tem
mais de 2000 orçamentos. O backend-lab não pagina `GET /api/client/quotes`; o adapter
Spring recorta a página sobre a lista recebida, e o de demonstração sobre a massa local.
O Angular usa a mesma paginação na listagem de orçamentos e na aba "Orçamentos com
Relatório"; filtros por situação e busca valem para a página carregada, como nas listas de
relatórios e equipamentos.

A consulta por ID combina `Id = '...' AND Opportunity.Account.CNPJ__c ...`.
O download em `/api/quotes/{id}/document` verifica essa mesma consulta antes de
carregar ou gerar o PDF. Um orçamento de outra empresa retorna 404, assim como um
ID inexistente. Sem CNPJs autorizados, a lista retorna `[]`, detalhe/documento
retornam 404 e nenhuma chamada ao Salesforce é feita. Um escopo inválido também
não consulta a origem. Os vínculos do JWT continuam valendo até sua expiração;
entre novamente para obter uma associação de empresa recém-alterada.

Configure `Salesforce__Url`, `Salesforce__GrantType`, `Salesforce__ClientId` e
`Salesforce__ClientSecret` no `backend-portal-web/.env` para o Compose (ou no
ambiente do processo para execução direta). A seção Salesforce abaixo contém o
exemplo completo. O registro do Salesforce (opções, cliente de token e
`SalesforceQuoteRepository`) só existe em `lactec` com `Portal__UseDemoData=false`, e as
quatro chaves são validadas na inicialização (`ValidateOnStart`): `Url` absoluta HTTP ou
HTTPS, `GrantType`, `ClientId` e `ClientSecret` não vazios. Faltando alguma, a API não
sobe. Em `backend-lab` e na demonstração elas não são lidas. A conexão de leitura usa o token OAuth do Salesforce, separado
dos tokens do portal e do Backend Auth. Falhas de configuração, conexão, timeout
ou resposta da origem retornam 502 sem expor seu corpo. Angular mostra o erro;
nenhum dado de demonstração substitui uma consulta recusada ou indisponível.

No modo `backend-lab`, os orçamentos sempre vêm do Spring pelos adapters C#, mesmo
com `Portal__UseDemoData=true`; essa opção não seleciona fixtures nem documentos
substitutos para essa fonte. A demonstração do Salesforce e seus documentos locais
permanece restrita ao modo `lactec`.

### Vínculo entre orçamento e AutoLAB (`OrcamentoCRM`)

O AutoLAB conhece o orçamento pela coluna `OrcamentoCRM`, que guarda só a forma nua
`2024_37569`. No Salesforce, o código da cotação (`Quote.Codigo_da_Cotacao__c`) vem como
`EAQ_2024_37569_V_1`. A regra mora no domínio, em `Portal.Domain/Quotes/LaboratoryQuoteKey`,
e `Quote.LaboratoryKey` é derivada de `Quote.QuotationCode`:

| Código da cotação | Chave no AutoLAB |
| --- | --- |
| `EAQ_2024_37569_V_1` (qualquer versão `_V_n`) | `2024_37569` |
| `EAQ_2024_37569` | `2024_37569` |
| `2024_37569` | `2024_37569` |
| ausente, vazio ou qualquer outra forma (`EAQ_2024_37569_UHAB`, `EAQ 2024_37569`, ...) | nenhuma |

O valor é aparado nas pontas; não há busca por prefixo nem uso do código da oportunidade
(`Opportunity.Codigo_da_Oportunidade__c`) como alternativa. A chave é usada na etapa do
laboratório de `GET /api/quotes` e `GET /api/quotes/{id}` (`laboratoryStage`) e nos
relatórios de `GET /api/quotes/{id}/reports`. Orçamento sem chave válida fica com
`laboratoryStage: null` e sem relatórios (204), sem consultar o AutoLAB. O campo público
`code` continua sendo o código da oportunidade no Salesforce, e é ele que aparece em
`quoteCode` da conclusão de `/api/quotes/{id}/reports`; cada relatório traz em `quoteCode`
o `OrcamentoCRM` do AutoLAB (forma nua).

O adapter de demonstração informa o código da cotação igual ao `code`
(`EAQ_2026_45482_V_1` → `2026_45482`). O adapter Spring usa o único código do
backend-lab como código da cotação: no formato acima ele vira chave; em outro formato
(`ORC-52`) o orçamento fica sem chave e o AutoLAB não é consultado.

### Demonstração com duas contas

Execute na raiz do repositório:

```bash
docker compose -f compose.auth.yaml up -d --build
```

O arquivo é independente e inicia banco, Backend Auth, banco local do AutoLAB
(`autolab-init`), API C# e frontend. Abra
http://localhost:4200. Não requer Salesforce, Backend Lab, `.env` nem cadastro manual.
As senhas são iguais aos e-mails:

| Cliente | Empresa / CNPJ | Orçamentos | Equipamentos / laudos |
| --- | --- | --- | --- |
| `client1@lactec.com` | EMPRESA DE ENERGIA / `30960572000183` | `1, 3, 6, 8, 10, 12, 14, 16, 18` | 6 / 11 |
| `client2@lactec.com` | DISTRIBUIDORA DE ENERGIA / `11222333000181` | `4, 5, 7, 9, 11, 13, 15, 17, 19` | 6 / 11 |

Os IDs de orçamento usam o prefixo `quote-`. Os valores, códigos, situações e descrições
são preservados; as empresas dos orçamentos correspondem às contas. O PDF de `quote-3`
também usa a empresa correta. Equipamentos, laudos e relatórios aparecem de forma
generalizada para as duas contas (`LabResults__Scope=all`): são os 6 equipamentos e
11 laudos da massa local. Como no AutoLAB real, `vwRelatoriosEnsaios` e `SituaProcess`
guardam o `OrcamentoCRM` na forma nua (`2026_45496`), e os orçamentos fictícios chegam a
ele pela chave do código da cotação (`EAQ_2026_45496_V_1`), então a etapa do laboratório e
os relatórios aparecem nos orçamentos locais.

Para usar o Salesforce real no lugar dos orçamentos de demonstração, defina
`PORTAL_USE_DEMO_DATA=false` no `.env` da raiz e as credenciais `Salesforce__*` em
`backend-portal-web/.env` (o Compose carrega esse arquivo); sem elas a API não inicia.
O padrão é `true`. Equipamentos, laudos e relatórios continuam vindo do AutoLAB.

`Portal__UseDemoData` é `false` por padrão na API (no `compose.auth.yaml`, vem de
`PORTAL_USE_DEMO_DATA`, padrão `true`) e não altera o fluxo Backend Auth → JWT.
Quando habilitado em `lactec`, lista, detalhe e PDF dos orçamentos usam o escopo de
CNPJs autenticado; orçamento de outra conta retorna 404. Requisições sem JWT
continuam retornando 401.

O seed cria também `lactec@lactec.com`, com a mesma senha, para administrar somente
o Backend Auth pelo Swagger/API. O token de integração e a chave JWT ficam nos
backends. O Compose tem padrões públicos para uso local, substituíveis por
`BACKEND_AUTH_SERVICE_TOKEN` e `PORTAL_JWT_SIGNING_KEY` no `.env` da raiz.
O Auth recebe o mesmo token configurado no C# e mantém apenas seu hash.

Reinícios preservam os dados e não redefinem as senhas ou permissões editadas.
`docker compose -f compose.auth.yaml down` preserva o banco; `down -v` apaga o
ambiente demo, permitindo recriar as contas com os padrões. O volume é separado
dos demais Composes. Este arquivo substitui o antigo overlay de rede externa e
não deve ser combinado com `compose.yaml`.

### Configuração e execução em lactec

1. Suba o serviço independente seguindo o [README do Backend Auth](../backend-auth/README.md),
   cadastre o primeiro administrador pelo link dos logs e entre no Swagger UI.
2. Crie o serviço com código `portal-web`, gere um token de serviço e crie um
   cliente ativo vinculado a esse serviço e, opcionalmente, a empresas.
3. Para usar o Salesforce real, mantenha `Portal__UseDemoData=false`, configure as
   credenciais Salesforce e exporte as variáveis abaixo no processo do C#.
4. Com o Backend Auth já iniciado, execute `dotnet run --project PortalHexagonalApi/Portal.Api`
   em `backend-portal-web/`. O `.env` sozinho não configura `dotnet run`.

Para `dotnet run` fora do Docker, exporte as variáveis do processo:
`Portal__DataSource=lactec`, `BackendAuth__BaseUrl=http://localhost:8081`,
`BackendAuth__ServiceCode=portal-web`, `BackendAuth__ServiceToken`,
`PortalJwt__Issuer`, `PortalJwt__Audience` e `PortalJwt__SigningKey`.
`PortalJwt__ExpirationSeconds` é opcional (900) e deve ser positivo. A chave deve
ser Base64 de pelo menos 32 bytes aleatórios, diferente de chaves do Backend Lab.
Não há credenciais ou segredo padrão. Configuração inválida impede o startup.
Os padrões públicos existem somente no Compose de demonstração independente.
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
serviço, `/auth/me`, falhas de integração, cooldown, CORS e OpenAPI. Os testes do
Salesforce usam HTTP simulado e conferem o SOQL enviado, múltiplas empresas,
ausência de vínculos, separação de clientes, bloqueio de detalhe/PDF fora do escopo,
paginação (contagem, `LIMIT`/`OFFSET` e cursor depois do `OFFSET` 2000) e o vínculo com o
AutoLAB pelo código da cotação. O frontend
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

Essas chaves só são exigidas em `lactec` com `Portal__UseDemoData=false` (ver
[Orçamentos do Salesforce por CNPJ](#orçamentos-do-salesforce-por-cnpj)). O token OAuth é
obtido e renovado internamente pelo adapter; não há rota pública para ele.

## Conexao ao AutoLAB (SQL Server)

As fontes `LabResults`, `EquipmentRegistry`, `LaboratoryProgress` e `ReportDocuments`
aceitam `sql-server`. Nesse modo a API le o banco `bdautolab_ensaios` pelo Entity
Framework Core (provider `Microsoft.EntityFrameworkCore.SqlServer`), somente leitura: sem
migrations, sem `SaveChanges` (o `DbContext` recusa), consultas LINQ sem tracking sobre as
views `vwDadosEnsaios`, `Equipamentos`, `SituaProcess` e `vwRelatoriosEnsaios` e sobre as
tabelas `RelatorioEmissao` e `FileData`.

As credenciais do AutoLAB real vem do ambiente ou do `.env` (nunca do `appsettings`
versionado). O `compose.auth.yaml` carrega primeiro os padrões públicos do banco local em
`docker/autolab/demo.env`, usando a mesma senha configurada para seu SQL Server. Depois
carrega `backend-portal-web/.env`, cujos valores de conexão AutoLAB têm precedência.
Sem esse arquivo, o demo local continua funcionando; com uma conexão externa configurada,
a API consulta essa origem. O `appsettings.Development.json` usa fontes desconectadas e
não define credenciais AutoLAB:

```env
LabResults__Source=sql-server
EquipmentRegistry__Source=sql-server
LaboratoryProgress__Source=sql-server
ReportDocuments__Source=sql-server

AutoLab__Server=LACTECSQL10,49933
AutoLab__Database=bdautolab_ensaios
AutoLab__User=projeto_portal
AutoLab__Password=<pedir ao responsavel pelo AutoLAB>
AutoLab__Encrypt=false
AutoLab__TrustServerCertificate=true
AutoLab__ReadOnlyIntent=true
AutoLab__CommandTimeoutSeconds=15
AutoLab__ConnectTimeoutSeconds=10
AutoLab__MaxPoolSize=10
AutoLab__MaxRowsPerQuery=5000
```

A connection string e montada com `SqlConnectionStringBuilder` e validada na
inicializacao (`ValidateOnStart`) quando alguma fonte e `sql-server`; com todas as fontes
fora do banco, a secao `AutoLab` nao e exigida. O `DbContext` e registrado uma vez, em
pool, mesmo com as quatro fontes ligadas.

Protecoes para nao pesar no banco do laboratorio:

- toda consulta tem limite no banco (`TOP` ou `OFFSET ... FETCH`) e, fora do escopo `all`,
  `WHERE` (empresa, serie, protocolo, codigo do orcamento ou identificacao do laudo);
- `AutoLab__MaxRowsPerQuery` limita as linhas de cada consulta; passar do limite responde
  502 em vez de truncar o resultado em silencio;
- listas grandes de series ou de orcamentos viram varios `IN` de ate 500 valores;
- `AutoLab__CommandTimeoutSeconds` curto e `AutoLab__MaxPoolSize` limitam tempo e conexoes;
- `ApplicationIntent=ReadOnly` (desligue com `AutoLab__ReadOnlyIntent=false` se a replica
  recusar conexoes de intencao de leitura);
- so as duas rotas de PDF leem coluna `varbinary` (`FileData.Content`); `PdfLiberado` nunca
  e lido.

### Paginacao

`GET /api/quotes`, `GET /api/equipments`, `GET /api/equipments/{id}/reports`, `GET /api/reports` e
`GET /api/quotes/{id}/reports` aceitam `page` (a partir de 1)
e `pageSize` (padrao 50, maximo 200). Valor invalido responde 400 com ProblemDetails. O
corpo continua sendo o array da pagina; os cabecalhos `X-Total-Count`, `X-Page` e
`X-Page-Size` (expostos no CORS, junto com `Content-Disposition` dos downloads) dizem o
total e a pagina servida. A paginacao acontece no
banco: primeiro as chaves (equipamentos do escopo, ou protocolos do equipamento) em
ordem fixa, depois as linhas so das chaves da pagina.

### PDF assinado do laudo

`GET /api/equipments/{id}/reports/{reportId}/document` devolve `application/pdf`. O laudo
e validado primeiro pelo caminho com escopo (o protocolo tem que estar no escopo e ser do
equipamento da rota); so entao a identificacao dele busca em `RelatorioEmissao` a versao
vigente (`GCRecord` nulo, sem substituto, maior revisao, mais recente) e o arquivo em
`FileData` pelo `Oid`. Equipamento ou laudo fora do escopo, ou laudo sem PDF
assinado, respondem 404.

Cada linha do historico traz `hasSignedDocument`: existe `RelatorioEmissao` nao apagado
com `PdfAssinado` cujo `FileData` nao esta apagado. A pagina inteira e resolvida numa
consulta so, com `IN` das identificacoes. O antigo `reportUrl` saiu do contrato.

### Relatorios (`vwRelatoriosEnsaios`)

`GET /api/reports?quoteCode=` lista `vwRelatoriosEnsaios` (filtro opcional por
`OrcamentoCRM` exato, na forma nua que o AutoLAB guarda, por exemplo `2026_45493`; é o
mesmo valor que cada relatório devolve em `quoteCode`, e não o `code` do orçamento),
ordenado por `DataPublicacao` desc, `DataEmissao` desc e `Oid`.
Cada item: `id` (o `Oid`, opaco), `identification`, `requestNumber`, `quoteCode`,
`sampleId`, `sampleName`, `client`, `status`, `scheduledDate`, `issueDate`,
`publicationDate` (datas `yyyy-MM-dd` ou `null`) e `hasSignedDocument` (`PdfAssinado`
preenchido). `GET /api/reports/{id}/document` segue `Oid` → `PdfAssinado` → `FileData`
(`GCRecord` nulo) dentro do escopo; sem PDF responde 404. No escopo `company`, a empresa e
comparada com `ClienteSolicitante`.

### Indicadores: tensao nominal e texto do relatorio

`physicalChemical.nominalVoltage` e `Equipamentos.Tensao` da placa, ou `null`.
`physicalChemical.report` e `dissolvedGases.report` sao `{ title, paragraphs }` do
`RelatorioEmissao` vigente da analise selecionada (sem selecao, da analise mais recente
de cada serie): `TituloRelatorio` e `Conclusao` quebrada por linha em branco. Sem
relatorio, `title` e `null` e `paragraphs` e vazio. Conformidade, veredito,
classificacao, metodo e limite continuam podendo vir `null`/`-`.

### Escopo de visibilidade

`LabResults__Scope` e obrigatorio:

- `all` (padrao do ambiente local): nenhuma consulta filtra empresa; todos os registros
  aparecem para qualquer usuario autenticado.
- `company`: filtra `vwDadosEnsaios.EmpresaTrafo` (e `ClienteSolicitante` nos relatorios)
  pela claim `inquilino` ou, na falta dela, por `LabResults__DevelopmentTenant`; sem
  nenhuma das duas responde 401.

Caminho futuro do filtro por CNPJ (a autenticacao esta sendo refeita): claim
`companyCnpjs` do JWT → `dbo.dCliente.Cnpj_Cpf` (colunas `Oid`, `Sigla`, `Nome`,
`RazaoSocial`, `Cnpj_Cpf`) → ligacao ainda a investigar com `RelatorioEmissao.Solicitacao`,
`vwRelatoriosEnsaios.ClienteSolicitante` ou `vwDadosEnsaios.EmpresaTrafo`. Casar por nome
nao serve: dos 56 `EmpresaTrafo` distintos de uma amostra, so 2 casam com `dCliente.Nome` e
1 com `RazaoSocial`.

**Risco no AutoLAB real:** em `all`, a listagem de equipamentos agrupa a view
`vwDadosEnsaios` inteira (`EmpresaTrafo` e `nvarchar(max)` sem indice, e nao ha filtro
seletivo), e `GET /api/reports` sem `quoteCode` ordena `vwRelatoriosEnsaios` inteira. Uma
consulta de levantamento que ordenou a view sem filtro levou 25,6 s em producao. Use
`all` so no banco local ate existir o filtro por CNPJ.

### Banco local do AutoLAB

`docker/autolab/01-create-database.sql` cria `autolab` com `SQL_Latin1_General_CP1_CI_AS`;
`02-create-objects.sql` cria `vwDadosEnsaios`, `Equipamentos`, `SituaProcess`,
`vwRelatoriosEnsaios` e `dCliente` como views com os nomes, colunas, tipos e nulabilidade
levantados em producao (sobre tabelas locais `*Local`), e `RelatorioEmissao`/`FileData`
como as tabelas XPO, com os indices. `03-load-sample.sql` e gerado, junto com
`PortalHexagonalApi/Portal.Tests/AutoLab/autolab-sample.json` (a mesma massa carregada no
SQLite da suite), a partir de `tools/massa-autolab.mjs`:

```bash
node tools/gerar-massa-autolab.mjs
```

A massa tem 11 laudos de 6 equipamentos (EMPRESA DE ENERGIA e DISTRIBUIDORA DE ENERGIA),
o cadastro de placas, as linhas de `SituaProcess` com os codigos dos orcamentos ficticios
na forma nua de `OrcamentoCRM` (`2026_45482`, nunca `EAQ_2026_45482_V_1`, como no AutoLAB
real; algumas linhas avulsas reproduzem as variantes reais `2026_49001_UHAB Agua Boa` e
`EAQ 2026_49002`, que nao viram chave de nenhum orcamento),
um relatorio por laudo com PDFs assinados de uma pagina, laudos sem PDF, uma revisao
substituida, um relatorio apagado (`GCRecord`) e um sem titulo nem conclusao. Para
recarregar: `docker compose -f compose.auth.yaml run --rm autolab-init`. Para `dotnet run`
fora do Docker, troque `AutoLab__Server` pelo endereco publicado da instancia.

## Limitações conhecidas e trabalho futuro

- **Aceite e recusa de proposta não voltam ao Salesforce.** A integração com o Salesforce é
  somente leitura. No Portal do Cliente, aceite, recusa, ordem de compra e confirmação de
  envio de amostra ficam em memória, durante a sessão do navegador
  (`frontend/src/app/services/quote-decision.service.ts`): recarregar a página ou entrar
  de outro dispositivo perde a decisão, e o comercial não a recebe. A gravação no
  Salesforce (ou no serviço que o representar) é trabalho futuro; as assinaturas do
  serviço Angular já seguem as rotas `POST /api/quotes/{id}/...` previstas.
- Filtro por situação e busca da listagem de orçamentos valem para a página carregada,
  como nas demais listas; filtrar no Salesforce é trabalho futuro.

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
