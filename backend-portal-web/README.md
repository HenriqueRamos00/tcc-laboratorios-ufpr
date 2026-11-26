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
| `lactec` | `BackendAuth__BaseUrl` → Spring Backend Auth2 independente | JWT HS256 emitido e validado pela API C# |

`lactec` é o modo Backend Auth2. A fonte é definida na inicialização, sem seleção por
requisição ou fallback entre os serviços. Valores ausentes e desconhecidos impedem
a inicialização. As configurações da fonte inativa são ignoradas. No modo `lactec`,
os orçamentos são consultados no Salesforce com os CNPJs autorizados no JWT.
Com `Portal__UseDemoData=true`, o C# usa orçamentos de demonstração. A opção não afeta
equipamentos, ensaios nem relatórios no modo Auth, que vêm do
AutoLAB filtrados pelos mesmos CNPJs do JWT (ver [Escopo de visibilidade](#escopo-de-visibilidade)).

`POST /auth/login` é público e recebe `{ "email": "...", "password": "..." }`.
O controller chama `ILoginUseCase` → `LoginService` → `IClientAuthenticator`.
No modo `lactec`, `BackendAuthAdapter` envia a senha intacta e o email normalizado
para `/api/auth/login`, com o Bearer de serviço configurado **somente no C#**.
Não há repetição automática nem redirecionamento; o timeout é de 10 segundos.
A API recusa identidades que não sejam `CLIENTE`, tenham outro `serviceCode` ou
estejam incompletas. Administradores do Backend Auth2 administram apenas esse serviço
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
`GET /auth/me` devolve o mesmo `user` usando os claims, sem consultar o Backend Auth2.
Login, OpenAPI e preflight CORS são públicos. No modo `backend-lab`, `/auth/me`
retorna 501 e as consultas de orçamento e relatórios encaminham o Bearer Spring à origem.
No Compose regular, equipamentos e indicadores consultam os endpoints de cliente Spring,
encaminhando o Bearer de cada requisição. O OpenAPI descreve o Bearer quando `lactec`
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
desliga apenas o provider AutoLAB; os documentos Spring são selecionados por
`Portal__DataSource=backend-lab`.
Em `compose.auth.yaml`, `ReportDocuments__Source=sql-server` seleciona os relatórios AutoLAB.
Não há fallback entre as duas origens.

As rotas `/api/reports/{id}/document` e `/api/reports/{id}/file` devolvem o mesmo documento
da fonte selecionada. Os IDs são opacos e devem ser reutilizados como recebidos.
`pdfAvailable` informa se o documento pode ser baixado; `hasSignedDocument` identifica
PDF assinado do AutoLAB e não é usado para declarar assinatura digital em documentos Spring.
O download mantém o PDF persistido, o nome do arquivo e `Cache-Control: no-store`.

`LabResults__Source=backend-lab` e `EquipmentRegistry__Source=backend-lab` usam os
cadastros e resultados publicados pelo Spring. Históricos e séries identificam o equipamento
pelo seu ID e consideram uma análise por execução. As datas Spring correspondem à publicação
do laudo, no fuso America/Sao_Paulo. `LaboratoryProgress__Source=not-connected`
mantém a etapa enviada pelo Spring. Os detalhes estão no
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

O cooldown pertence à conta no Backend Auth2, não ao IP do C#. Angular mostra a
espera e desabilita novos envios sem tentar novamente sozinho. CORS expõe
`Retry-After`. Login e `/auth/me` usam `Cache-Control: no-store`; erros usam
ProblemDetails sem expor respostas internas. Senhas antigas continuam aceitas no
login; a política de composição é aplicada ao cadastro no Backend Auth2.

### Orçamentos do Salesforce por CNPJ

No modo `lactec`, com `Portal__UseDemoData=false`, a API consulta os orçamentos do
Salesforce autorizados para as empresas da sessão. Os CNPJs vêm do JWT validado;
o cliente não pode ampliar esse escopo por parâmetros, cabeçalhos ou IDs de empresas.

`GET /api/quotes` aceita `page` a partir de 1 e `pageSize` com padrão 50 e máximo 200
(ver [Paginacao](#paginacao)). Valores inválidos respondem 400. O corpo é o array da
página; `X-Total-Count`, `X-Page` e `X-Page-Size` informam o total e a página servida.
No frontend, os filtros por situação e busca valem para a página carregada.

O detalhe e o download em `/api/quotes/{id}/document` exigem um orçamento autorizado.
Orçamento de outra empresa ou identificador inexistente respondem 404. Sem CNPJs
autorizados, a lista retorna `[]` e detalhe/documento retornam 404. Alterações de
vínculos de empresas exigem novo login para atualizar a sessão.

Para usar a integração real, configure as credenciais conforme a seção
[Configuracao do Salesforce](#configuracao-do-salesforce). A configuração é validada
na inicialização quando `Portal__DataSource=lactec` e `Portal__UseDemoData=false`.
Falhas de conexão, timeout ou resposta inválida retornam 502; o portal exibe o erro.

No modo `backend-lab`, os orçamentos sempre vêm do Spring pelos adapters C#, mesmo
com `Portal__UseDemoData=true`; essa opção não seleciona fixtures nem documentos
substitutos para essa fonte. A demonstração do Salesforce e seus documentos locais
permanece restrita ao modo `lactec`.

### Demonstração com Auth2

Execute na raiz do repositório:

```bash
docker compose -f compose.auth.yaml up -d --build
```

O arquivo inicia Oracle com Auth2 e migrações Flyway, SQL Server (banco `portal`), API C#
e frontend. Abra http://localhost:4200. Configure `backend-portal-web/.env` com a conexão
do AutoLAB real e conecte-se à VPN do Lactec.

As contas, senhas iniciais, empresas, orçamentos disponíveis e opções de configuração
estão no [README do Auth2](../backend-auth2/README.md#demonstração-do-portal), junto
com as instruções de persistência e reinicialização do ambiente.

`Portal__UseDemoData` é `false` por padrão na API. No `compose.auth.yaml`, vem de
`PORTAL_USE_DEMO_DATA`, com padrão `true`, e não altera o fluxo Auth2 → JWT.
Quando habilitado em `lactec`, lista, detalhe e PDF dos orçamentos usam o escopo de
CNPJs autenticado; orçamento de outra conta retorna 404. Requisições sem JWT
continuam retornando 401. Equipamentos, laudos e relatórios vêm do AutoLAB real,
filtrados pelos mesmos CNPJs. As decisões do cliente ficam no banco `portal` local.

O C# encaminha `POST /auth/recoveries` e `POST /auth/password-resets` ao Spring no
modo `backend-lab` ou ao Auth2 no modo `lactec`. O backend escolhido envia o e-mail.
Configure `SPRING_MAIL_*` para Backend Lab ou `AUTH_MAIL_*` para Auth2, conforme o
[guia de e-mail](../README.md#e-mail-e-recuperação-de-senha).

### Configuração e execução em lactec

1. Suba o serviço independente seguindo o [README do Backend Auth2](../backend-auth2/README.md).
   Cadastre o primeiro administrador com o segredo de instalação e entre pela API
   administrativa; a documentação do contrato fica em `backend-auth2/docs/openapi.json`.
2. Crie o serviço com código `portal-web`, gere um token de serviço e crie um
   cliente ativo vinculado a esse serviço e, opcionalmente, a empresas. Confirme o
   e-mail do cliente para habilitar seu login.
3. Para usar o Salesforce real, mantenha `Portal__UseDemoData=false`, configure as
   credenciais Salesforce e exporte as variáveis abaixo no processo do C#.
4. Com o Backend Auth2 já iniciado, execute `dotnet run --project PortalHexagonalApi/Portal.Api`
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

Em `backend-lab`, configure `Portal__DataSource=backend-lab`,
`LabResults__Source=backend-lab`, `EquipmentRegistry__Source=backend-lab`,
`LaboratoryProgress__Source=not-connected`, `ReportDocuments__Source=not-connected`
e `BackendLab__BaseUrl` (no Compose da raiz, `BACKEND_LAB_BASE_URL`). Por exemplo,
`http://localhost:8080` fora do Docker ou `http://backend-lab:8080` no Compose integrado.

Em implantação, use HTTPS entre navegador e C# e entre C# e Backend Auth2, restrinja
o Backend Auth2 à rede dos servidores e mantenha token de serviço/chave JWT fora do
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
Salesforce usam HTTP simulado e cobrem múltiplas empresas, ausência de vínculos,
separação de clientes, bloqueio de detalhe/PDF fora do escopo e paginação.
O frontend cobre armazenamento/restauração, envio restrito do Bearer, logout, mensagens e
espera do login. Para um smoke real, mantenha o Backend Lab parado, use o Backend
Auth2 com Oracle e AutoLAB com SQL Server, autentique pelo C#, consulte `/auth/me` e `/api/quotes` com o
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
aceitam `sql-server`. A integração com o AutoLAB é somente leitura e exige acesso
pela VPN do Lactec. Solicite os dados de conexão e uma credencial de leitura ao
responsável pelo AutoLAB.

As credenciais vêm do ambiente ou do `.env`, sem serem incluídas no `appsettings`
versionado. Tanto o `docker-compose.yml` deste módulo quanto o `compose.auth.yaml`
da raiz leem `backend-portal-web/.env`. O `appsettings.Development.json` usa fontes
desconectadas e não define credenciais AutoLAB:

```env
LabResults__Source=sql-server
EquipmentRegistry__Source=sql-server
LaboratoryProgress__Source=sql-server
ReportDocuments__Source=sql-server

AutoLab__Server=<servidor-e-porta-fornecidos-pelo-responsavel>
AutoLab__Database=<banco-fornecido-pelo-responsavel>
AutoLab__User=<usuario-de-leitura>
AutoLab__Password=<senha-fornecida-pelo-responsavel>
AutoLab__Encrypt=false
AutoLab__TrustServerCertificate=true
AutoLab__ReadOnlyIntent=true
AutoLab__CommandTimeoutSeconds=15
AutoLab__ConnectTimeoutSeconds=10
AutoLab__MaxPoolSize=10
AutoLab__MaxRowsPerQuery=5000
```

A configuração é validada na inicialização quando alguma fonte usa `sql-server`.
Com todas as fontes desconectadas, a seção `AutoLab` não é exigida.
`AutoLab__CommandTimeoutSeconds` e `AutoLab__MaxPoolSize` limitam o tempo das consultas
e as conexões. `AutoLab__MaxRowsPerQuery` limita o volume de cada consulta; exceder o
limite responde 502, sem truncar silenciosamente os resultados.

### Paginacao

`GET /api/quotes`, `GET /api/equipments`, `GET /api/equipments/{id}/reports`, `GET /api/reports` e
`GET /api/quotes/{id}/reports` aceitam `page` (a partir de 1)
e `pageSize` (padrao 50, maximo 200). Valor invalido responde 400 com ProblemDetails. O
corpo continua sendo o array da pagina; os cabecalhos `X-Total-Count`, `X-Page` e
`X-Page-Size` (expostos no CORS, junto com `Content-Disposition` dos downloads) dizem o
total e a pagina servida.

### Busca de equipamentos

`GET /api/equipments?search=` funciona nas duas fontes. No modo Spring, a API encaminha
o termo ao Backend Lab, que busca por trecho do número de série ou da TAG sem distinguir
maiúsculas, respeitando as empresas autorizadas antes da contagem e da paginação. O termo
é aparado, vazio significa sem filtro e o limite é de 100 caracteres; caracteres de
`LIKE` são tratados como texto literal.

No modo AutoLAB, a busca considera trechos do número de série, da identificação do
laudo ou da TAG, respeitando as empresas autorizadas. A comparação não distingue
maiúsculas; o termo é aparado, vazio significa sem filtro e mais de 100 caracteres
responde 400 (`invalid-search`). Caracteres especiais são tratados como texto literal.
`X-Total-Count` informa o total da busca. Cada equipamento encontrado vem com todos
os laudos dele disponíveis para a conta.

### PDF assinado do laudo

`GET /api/equipments/{id}/reports/{reportId}/document` devolve `application/pdf` quando
há um documento assinado disponível. O laudo deve pertencer ao equipamento informado
e estar autorizado para a conta. Equipamento ou laudo fora do escopo, ou laudo sem
PDF assinado, respondem 404. O campo `hasSignedDocument` do histórico informa a
disponibilidade do documento assinado.

### Laudos do equipamento em um arquivo

`GET /api/equipments/{id}/reports/archive` devolve `application/zip` com os PDFs
assinados dos laudos do equipamento, num arquivo chamado `laudos-<equipamento>.zip`.
Cada laudo entra na pasta do tipo de ensaio, `Cromatografia/`, `Fisico-Quimica/` ou
`Outros-Ensaios/`, com o nome `<data>_<número do laudo>.pdf`. Nome repetido recebe
sufixo numérico. O arquivo é transmitido conforme é montado, sem ficar inteiro em
memória.

O escopo é o mesmo das demais rotas: equipamento de outra empresa responde 404.
Equipamento sem análise responde 404 (`equipment-without-analysis`) e identificador
ilegível responde 400. Quando nenhum laudo do equipamento tem PDF assinado, a
resposta é 204, sem corpo. Acima de 200 laudos a resposta é 422
(`report-archive-too-large`), informando a quantidade encontrada e o máximo aceito,
porque o histórico já permite baixar laudo a laudo. Falha da fonte de documentos
responde 502 (`report-document-source-unavailable`).

### Relatorios

`GET /api/reports?quoteCode=` lista os relatórios das empresas da sessão. O filtro
opcional `quoteCode` usa o valor devolvido nesse campo pelos relatórios e apenas
restringe o conjunto já autorizado.

Cada item inclui `id` opaco, `identification`, `requestNumber`, `quoteCode`, `sampleId`,
`laboratory`, `client`, `status`, `scheduledDate`, `issueDate`, `publicationDate` e
`hasSignedDocument`. As datas usam `yyyy-MM-dd` ou `null`. `sampleName` só é preenchido
no modo Spring.

`GET /api/reports/{id}/document` baixa o documento autorizado. Documento indisponível,
identificador desconhecido ou registro de outra empresa respondem 404.

### Indicadores: tensao nominal, laudos e conformidade

`physicalChemical.nominalVoltage` informa a tensão nominal do equipamento, ou `null`.
`physicalChemical.reports` e `dissolvedGases.reports` listam os laudos do equipamento que
têm aquela seção, do mais recente para o mais antigo, como
`{ identification, date, resultLabel, title, paragraphs }`. No modo Auth o texto vem do PDF
assinado de cada laudo (os 24 mais recentes, lidos em sequência e guardados em memória por
arquivo): `resultLabel` é a célula RESULTADO da tabela (`NORMAL`, `Descarga elétrica`...),
`title` e `paragraphs` são o bloco `LAUDO DA ANÁLISE CROMATOGRÁFICA` ou
`LAUDO DAS ANÁLISES FÍSICO-QUÍMICAS`, e `date` é a data de emissão. PDF ausente ou ilegível
deixa o laudo na lista com `resultLabel`/`title` `null` e `paragraphs` vazio. No modo
Backend Lab a lista tem um laudo só (o da análise selecionada, ou o mais recente da
seção), sem rótulo.

`conformity` de cada bloco vem do rótulo do laudo selecionado na rota, ou do mais recente
da seção: `NORMAL` (sem diferenciar maiúsculas) vira `normal`, outro rótulo vira
`nao-conforme`, e sem rótulo é `null`. `verdict` repete o rótulo. A seção que a análise
selecionada não tem também usa o laudo mais recente dela, em vez de ficar sem conformidade.
Classificação, método e limite podem vir `null`/`-`.

`oilType` informa o óleo isolante do equipamento como `{ kind, reported }`, onde `reported`
é o texto do cadastro e `kind` é `mineral`, `natural-ester`, `silicone`, `unrecognized`
(o cadastro informou um tipo que não corresponde a nenhum dos anteriores) ou `not-informed`.
O cadastro ausente responde `not-informed`: o tipo nunca é presumido mineral.

O limite de cada ensaio físico-químico depende desse tipo. Quando o laboratório publica o
limite junto do resultado, ele é mantido como veio. Sem isso, só o óleo mineral recebe o
limite curado do projeto; os demais vêm com `limit` nulo e `limitAbsence` dizendo por quê:
`oil-type-without-criterion` para éster natural e silicone, que o projeto não tem critério
para avaliar, `oil-type-unrecognized` para o tipo informado e não reconhecido, e
`oil-type-not-informed` para o cadastro sem tipo.

### Escopo de visibilidade

Os dados do AutoLAB e os orçamentos são visíveis somente para as empresas da sessão. O
escopo é a claim `companyCnpjs` do JWT que a própria API emitiu e validou, e não existe
outra origem: query string, corpo, rota, cabeçalho e configuração não o alteram
(`CompanyScopeResolution` só recebe o `ClaimsPrincipal`). Não há escopo "todas as
empresas".

- Sem sessão válida: 401.
- Sessão sem CNPJ, ou com qualquer valor da claim fora do formato de CNPJ: escopo vazio.
  Listas respondem 200 vazias e todo identificador responde 404. Um valor malformado
  fecha o escopo inteiro; ele não é descartado para aproveitar os demais.
- Identificador desconhecido e identificador de outra empresa respondem o mesmo 404, com
  o mesmo corpo.
- Falha da fonte responde 502; nunca vira lista vazia nem escopo maior.
- As respostas de equipamentos, indicadores, relatórios e orçamentos saem com
  `Cache-Control: no-store`, e o CNPJ não é devolvido em corpo, cabeçalho ou log (nas
  consultas ele viaja como parâmetro).

Os orçamentos consultados no Salesforce seguem o mesmo escopo de empresas da sessão.
Documento, relatórios do orçamento e etapa do laboratório partem sempre do orçamento
já autorizado.

### Banco do portal (decisões do cliente)

O AutoLAB e a Salesforce são somente leitura para o portal. O que o cliente decide no portal
fica no banco próprio `portal`, num SQL Server em contêiner:

- **Serviço:** `portal-sqlserver`, no `docker-compose.yml` deste diretório, com volume
  `portal-sqlserver-data` e porta `127.0.0.1:${PORTAL_SQLSERVER_PORT:-1434}` para inspeção.
  A senha de SA vem de `PORTAL_SQLSERVER_SA_PASSWORD` no `.env`.
- **Criação:** a API aplica as migrações do EF Core ao subir (`PortalStore__Source=sql-server`)
  e cria o banco e o esquema `portal` se não existirem. As migrações ficam em
  `Portal.Adapters/PortalStore/Migrations`; para gerar uma nova:
  `dotnet ef migrations add <Nome> --project Portal.Adapters --startup-project Portal.Adapters --context PortalStoreDbContext --output-dir PortalStore/Migrations`.
- **Tabelas:** `portal.ClientDecisions` (orçamento, tipo, autor e horário; índice único por
  orçamento e vaga, uma para aceite/ordem de compra/recusa e outra para o envio da amostra),
  `portal.AcceptanceTerms` (termo de aceite), `portal.AcceptanceInvoiceEmails` (e-mails para
  envio da NFS-e, um por linha: termo, posição e endereço, com índice único por termo e
  endereço), `portal.PurchaseOrders` (PDF, nome, tamanho e SHA-256) e `portal.Rejections`
  (justificativa).
- **Migração única:** enquanto o banco não guarda dado a preservar, mudança de esquema
  regenera a `CriarBancoDoPortal` em vez de empilhar outra. Um banco criado pela versão
  anterior precisa ser recriado (apague o volume `portal-sqlserver-data`).
- **Rotas** (só no modo `lactec`, com o cliente logado):
  `POST /api/quotes/{id}/acceptance` (JSON do termo), `POST /api/quotes/{id}/purchase-order`
  (multipart, campo `file`, PDF de até 10 MB), `POST /api/quotes/{id}/sample-shipment` e
  `POST /api/quotes/{id}/rejection` (`{ "justification": "..." }`, até 2000 caracteres).
  Respondem 201 com a decisão, 404 quando o orçamento não é da empresa do cliente, 409 quando
  a etapa não aceita a decisão ou ela já existe, e 501 sem banco do portal configurado.
- **E-mails de NFS-e no termo:** `billing.invoiceEmails` é uma lista de 2 a 5 endereços, cada
  um com até 254 caracteres ASCII, sem repetição (a comparação ignora maiúsculas). O servidor
  não separa endereços de um texto só: fora da regra, responde 400 nomeando o campo
  (`billing.invoiceEmails` ou `billing.invoiceEmails[posição]`).
- **Regras:** aceitar, enviar ordem de compra ou recusar só com proposta pronta (a partir da
  negociação) e antes de "aprovado pelo cliente"; confirmar o envio da amostra só em
  "aprovado pelo cliente". A etapa considerada é a mais avançada entre a comercial, a do
  laboratório e as decisões já gravadas. `GET /api/quotes` e `GET /api/quotes/{id}` devolvem a
  decisão mais recente em `clientDecision`.
- **Em produção:** use um login próprio do banco `portal`, em vez de `sa`.

## Limitações conhecidas e trabalho futuro

- **Aceite e recusa de proposta não voltam ao Salesforce.** A integração com o Salesforce é
  somente leitura. Aceite, recusa, ordem de compra e confirmação de envio de amostra ficam
  no banco do portal (veja "Banco do portal"), e o comercial ainda não os recebe. Levar essas
  decisões à Salesforce (ou ao serviço que o representar) é trabalho futuro.
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
