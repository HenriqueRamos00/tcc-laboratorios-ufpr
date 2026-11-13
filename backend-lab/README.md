# Backend Lab

Backend monolítico com os casos de uso e a persistência das operações internas do TCC - Laboratórios UFPR. Utiliza Java 21, Spring Boot, Spring Web, Spring Data JPA e PostgreSQL.

O Portal Interno acessa diretamente esta API para autenticação e gestão de técnicos, empresas e clientes: `Portal Interno -> Spring -> Use Case -> Repository -> PostgreSQL`. O Spring valida a sessão e o perfil atual do usuário, aplica as regras internas e persiste os dados. O proxy Angular em desenvolvimento e o Nginx em produção encaminham `/api` ao Spring.

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
│   ├── usecase/
│   ├── service/
│   └── validation/
├── domain/
│   ├── exception/
│   ├── model/entity/
│   └── repository/
├── infrastructure/
│   ├── config/
│   ├── persistence/
│   └── security/
└── presentation/
    ├── advice/
    └── rest/
```

- `application`: DTOs, casos de uso, validações cadastrais e componentes de autorização e vínculos. Cada operação relevante deve ter seu próprio Use Case;
- `domain`: entidades JPA, exceções e interfaces de repository;
- `infrastructure`: configurações técnicas, detalhes de persistência e integrações externas;
- `presentation`: controllers REST e tratamento global de erros.

Controllers devem permanecer pequenos. Casos de uso coordenam as regras de negócio e a persistência; componentes auxiliares concentram validações e regras compartilhadas com responsabilidade definida. Entidades JPA não devem ser retornadas diretamente pela API. Exceções de negócio expressam o motivo da falha, e a apresentação define o código HTTP.

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

A API ficará disponível em `http://127.0.0.1:8080` para desenvolvimento local. O Compose aguarda o PostgreSQL ficar saudável antes de iniciar o backend e monitora a API pelo endpoint `/health`.

Para um Portal Interno em Compose separado acessar o Spring por
`host.docker.internal`, publique a porta fora do loopback:

```bash
APP_BIND_ADDRESS=0.0.0.0 docker compose up --build -d
```

Essa configuração permite acesso pelas interfaces do host. O Compose integrado
da raiz usa a rede Docker entre os serviços e dispensa esse ajuste.

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

Para criar o administrador inicial, preencha `AUTH_SEED_ADMIN_*` no arquivo `.env`. As variáveis `AUTH_SEED_TECHNICIAN_*` permitem criar um técnico de desenvolvimento. Os demais técnicos podem ser cadastrados pelo Portal Interno. As senhas são armazenadas com BCrypt.

### Dashboard do Administrador

`GET /api/admin/dashboard` retorna os indicadores globais do banco PostgreSQL da aplicação e exige perfil ADMIN. A resposta contém `totalQuotes`, `pendingAcceptance`, `activeClients` e `totalQuotedValue`; as três quantidades são inteiras e o valor monetário é serializado como número decimal.

`totalQuotes` conta todos os orçamentos persistidos, sem filtro de status, data ou empresa. `pendingAcceptance` conta somente o status comercial `PendingAcceptance`. `totalQuotedValue` soma o `totalPrice` persistido de todos os orçamentos e status, sem recalcular os itens; quando não há propostas, retorna zero. `activeClients` conta clientes cujo usuário tem perfil CLIENTE e está ativo, uma vez por cadastro, independentemente de seus vínculos com empresas.

Sem autenticação válida, a API retorna 401. Técnicos autenticados recebem 403. Tokens de clientes são recusados pelo filtro de autenticação nas rotas internas e recebem 401.

### Dashboard do Técnico

`GET /api/technician/dashboard` é exclusivo do perfil TECNICO e consulta o PostgreSQL da aplicação. Retorna três contagens de execuções: `testsInExecution` considera `INCOMPLETE`; `testsAwaitingApproval` considera `COMPLETE`; `testsFinished` reúne `FINALIZED`, `DRAFTING_REPORT` e `REPORT_PUBLISHED`. `VALIDATED` e `CANCELLED` ficam fora desses indicadores, que não representam uma divisão de todos os ensaios.

`pendingReportsTotal` conta os relatórios em `PENDING_VALIDATION`. `pendingReports` apresenta até seis deles, por ID decrescente, com `id`, `companyName`, `type` e `validationStatus`. O total independe do limite da prévia; medições e documentos adicionais não multiplicam as contagens de ensaios. A consulta não altera dados nem retorna PDFs ou snapshots.

Sem registros, a resposta é 200 com contagens zero e lista vazia. Sem autenticação válida, retorna 401; administradores recebem 403 e tokens de clientes são recusados pelo filtro de autenticação interno. A fonte atual é o banco Spring; a integração real com o AutoLAB permanece pendente.

`reportAnalytics` acrescenta `referenceDate`, `monthlyReports` e `pendingReportAge`. A atividade mensal cobre os seis meses até a consulta, incluindo o mês atual parcial, no fuso `America/Sao_Paulo`. Cada documento é contado pela sua data de validação e, separadamente, pela data de publicação; meses sem eventos retornam zero. Eventos posteriores à consulta ficam fora da janela. Alterações no status técnico do ensaio não mudam esses eventos documentais.

A idade considera apenas documentos aguardando validação, desde a preparação (`created_at`), em dias de calendário de São Paulo. As faixas são até 7 dias, de 8 a 15 dias e 16 dias ou mais. Elas abrangem todas as pendências, independentemente do limite de seis itens da prévia. `oldestCreatedOn` informa a data de preparação mais antiga e é nulo quando não há pendências. As faixas não definem prazo contratual nem atraso.

### Consulta de orçamentos do cliente

`GET /api/client/quotes` lista os orçamentos das empresas ativas vinculadas ao cliente autenticado. `GET /api/client/quotes/{id}` consulta um orçamento e `GET /api/client/quotes/{id}/document` retorna o PDF anexado ou gera um PDF a partir dos dados persistidos quando não há anexo. A autorização considera os vínculos atuais do cliente antes de consultar ou gerar o documento; orçamento de empresa sem vínculo retorna 403 e orçamento inexistente retorna 404.

As respostas de lista e detalhe preservam o status comercial (`PendingAcceptance`, `InProgress`, `Finished`, `Refused` ou `Cancelled`) e retornam `stage` como texto não nulo, no vocabulário já reconhecido pelo Portal do Cliente. As etapas e aliases são definidos por `EtapaAcompanhamentoOrcamento`; a escrita rejeita etapas vazias ou desconhecidas. Sem ensaios, mantém-se a etapa persistida válida, mesmo que o status comercial mude. Recusa e cancelamento comerciais prevalecem com `Fechado recusado Lactec` e `Cancelado`. Com ensaios, o acompanhamento usa o menor avanço dos ativos; cancelados são desconsiderados quando há ativos, e todos cancelados apresentam `Cancelado`. O cadastro já inicia o acompanhamento em `Em execução`, mantendo o status interno `incomplete`; editar um ensaio não reinicia sua etapa. O cálculo não aceita comercialmente a proposta nem modifica o orçamento durante consultas. `Finalizado` não significa relatório publicado; execuções publicadas precisam de referência de laudo e data persistidas. A disponibilização dos laudos e as ações de transição HU26 permanecem próprias. A listagem consulta os estados necessários dos ensaios em lote, sem carregar medições ou PDFs. A leitura do documento não grava o PDF gerado nem altera o orçamento.

## Execução local

Inicie um PostgreSQL local com um banco chamado `backend_lab` e configure as variáveis de conexão. Em seguida, execute:

```bash
./mvnw spring-boot:run
```

As principais variáveis da aplicação são:

| Variável | Padrão | Uso |
| --- | --- | --- |
| `SERVER_PORT` | `8080` | Porta HTTP da API |
| `APP_BIND_ADDRESS` | `127.0.0.1` | Interface do host para publicar a porta no Docker Compose |
| `DB_URL` | `jdbc:postgresql://localhost:5432/backend_lab` | URL JDBC do PostgreSQL |
| `DB_USERNAME` | `backend_lab` | Usuário do banco |
| `DB_PASSWORD` | `backend_lab` | Senha do banco |
| `JPA_DDL_AUTO` | `update` | Estratégia de schema do Hibernate |
| `JWT_SECRET` | valor de desenvolvimento | Chave de assinatura do JWT; altere em ambientes reais |
| `JWT_EXPIRATION_SECONDS` | `3600` | Validade do JWT em segundos |

As configurações de JWT pertencem ao Spring e são fornecidas pelo `.env` da raiz
no Compose integrado ou pelas variáveis de ambiente na execução isolada. Os tokens
contêm identidade, perfil, emissão e expiração. A API
confere a assinatura HS256, a expiração, a data de emissão e o identificador
imutável, e consulta a situação e o perfil atuais do usuário no banco. As rotas
separam o acesso de clientes e usuários internos pelo perfil. Alterar o segredo
exige novo login.

O Java normaliza nome, e-mail, unidade e especialidade antes de validar os
limites, garante a unicidade do e-mail e valida a senha inicial. A senha não é
normalizada; uma senha omitida na edição preserva o hash atual. Os erros públicos
seguem o formato `{ "message": "..." }`.

O arquivo `.env` é local e não deve conter credenciais reais versionadas. Use `.env.example` como referência.


### Manutenção de ensaios

O Portal Interno consulta `GET /api/tests`, abre `GET /api/tests/{id}` e usa `POST`/`PUT` para criar ou substituir uma execução. As consultas de seleção ficam em `/api/tests/references/quotes`, `/samples`, `/analyzed-equipment`, `/technicians` e `/instruments`; os formatos conhecidos usam `/api/tests/measurement-templates/{analysisType}`. Todas essas rotas exigem um usuário ativo com perfil TECNICO.

O request identifica a amostra por `sampleId` existente ou por `newSample` com orçamento, código, descrição e equipamento analisado opcional. As alternativas são mutuamente exclusivas. A amostra nova e a execução são gravadas na mesma transação; não há CRUD independente nem gestão de coleta, recebimento, estoque ou descarte. O código de amostra mantém a unicidade global existente. Equipamentos analisados são filtrados pela empresa do orçamento e não se confundem com instrumentos laboratoriais utilizados.

As opções de orçamento e as novas associações aceitam somente propostas em `InProgress` ou `Finished`. Criação e troca para proposta pendente, recusada ou cancelada retornam 409. A edição pode manter o orçamento original mesmo que seu status comercial tenha se tornado inelegível; o detalhe histórico continua carregando esse vínculo.

Uma execução mantém suas medições, instrumentos laboratoriais, classificação informada e texto técnico em rascunho na base PostgreSQL. A manutenção usa a base própria Spring/PostgreSQL; integração com AutoLAB não faz parte do escopo. O código do ensaio é gerado no servidor. A edição exige `version` e usa controle otimista; conflitos retornam `test-version-conflict`, enquanto colisões do código de amostra retornam `sample-code-conflict`, ambos em 409. Quantidades são decimais em texto JSON, com limite de 16 dígitos inteiros e 8 casas. O tipo de cada resultado é informado explicitamente para preservar texto como `ND` ou `12,30`. Resultados `quantity`, `not-detected`, `not-performed` e `text` mantêm significados distintos. A classificação não é calculada automaticamente e o texto não é relatório publicado.

O seed da HU25 é opt-in por `DEMO_SEED_ENABLED=true`, depende das propostas/técnicos demonstrativos existentes e roda uma única vez por versão. Ele cria amostras e instrumentos demonstrativos, três execuções com quatro formas de resultado e datas coerentes. O seed não importa os JSONL do Portal do Cliente e não representa dados oficiais do AutoLAB.

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

## Empresas, clientes e orçamentos

O Portal Interno consome diretamente `/api/companies` e `/api/clients` no Spring. Cada entidade possui listagem, criação, edição e PATCH de situação, exclusivos ADMIN. Clientes reutilizam `Usuario` com perfil CLIENTE e têm vários vínculos com empresas; senha inicial é obrigatória e a edição pode omiti-la. E-mail é único entre todos os perfis. Desativar preserva histórico.

`POST /api/auth/login` autentica somente CLIENTE ativo. O login interno continua exclusivo ADMIN/TECNICO. Ambos emitem tokens com o mesmo formato; cada família de rotas verifica o perfil permitido e a situação atual do usuário. A resposta pública usa IDs string em login e gestão; as entidades Java mantêm chaves numéricas internamente, e os endpoints de técnicos preservam IDs numéricos.

O Portal do Cliente acessa a API C#. No modo `backend-lab`, o C# encaminha o login para `/api/auth/login` e consulta os orçamentos em `/api/client/quotes`, `/api/client/quotes/{id}` e `/api/client/quotes/{id}/document`, adaptando as respostas Spring ao contrato já consumido pelo portal. Para entrar nesse modo, use uma conta de cliente ativa cadastrada no Backend Lab.

As consultas privadas `/api/client/quotes`, `/{id}` e `/{id}/document` conferem usuário, vínculos e situação das empresas atuais. Os bytes de um PDF anexado são consultados somente após a autorização; sem anexo, o endpoint do cliente gera o documento a partir do orçamento autorizado. Empresa sem vínculo ativo retorna 403 e orçamento inexistente retorna 404. Cliente ativo sem empresas ativas pode autenticar e recebe lista vazia. Remover vínculo ou desativar usuário/empresa afeta o token anterior na próxima requisição. Na rota administrativa `/api/quotes/{id}/document`, documento não anexado continua retornando 404.

### Consulta e manutenção administrativa de orçamentos

As rotas administrativas de `/api/quotes` são exclusivas de ADMIN. O parâmetro opcional `status` da listagem aceita somente os códigos comerciais `PendingAcceptance`, `InProgress`, `Finished`, `Refused` e `Cancelled`, definidos por `StatusComercialOrcamento`; valor desconhecido retorna 400. A atualização também exige um código comercial. O campo de etapa permanece textual no contrato, validado pelo conjunto de etapas de acompanhamento e separado do status comercial.

`GET /api/quotes` pesquisa parcialmente por código, nome, descrição ou razão social da empresa. A paginação começa em 1, usa `pageSize=20` por padrão e aceita de 1 a 100 itens por página. A resposta contém `items`, `page`, `pageSize` e `totalItems`, ordenada por data de criação decrescente e ID decrescente como desempate. Cada item contém `id`, `code`, `name`, `status`, `stage`, `description`, `companyName`, `externalContactName`, `externalContactEmail`, `totalPrice`, `createdDate` e `hasDocument`. A listagem não carrega os itens de serviço nem os bytes do PDF.

`GET /api/quotes/{id}` retorna os campos atuais e acrescenta `companyId`, `laboratoryName`, `scope` e `items`. Registros anteriores à manutenção têm laboratório e escopo nulos, itens vazios e usam como contato externo o contato da empresa. `GET /api/quotes/{id}/document` baixa o PDF existente em `application/pdf`; detalhe e documento retornam 404 quando o recurso não existe.

`POST /api/quotes` cria orçamento e retorna 201; `PUT /api/quotes/{id}` atualiza e retorna 200. Ambos aceitam JSON ou `multipart/form-data`, com parte `quote` em `application/json` e parte opcional `document` em PDF. O corpo usa `code`, `companyId`, `name`, `laboratoryName`, `description`, `scope`, `externalContactName`, `externalContactEmail`, `status`, `totalPrice` e `items`. Cada item contém `testType`, `sampleCount`, `standard` e `unitPrice`. O nome do laboratório é texto livre; os itens pertencem ao orçamento e não criam ensaios, amostras ou serviços independentes.

O código é obrigatório, tem até 80 caracteres, é aparado nas extremidades, mantém a caixa informada e é único. A criação exige empresa ativa, status `PendingAcceptance` e define a etapa `Negociação` e a data no servidor. A atualização preserva etapa e data e exige um dos cinco estados comerciais. O status é um enum no domínio e na persistência; valores armazenados fora dos cinco códigos impedem a leitura do orçamento e devem ser migrados antes da inicialização do serviço. A empresa original pode ser mantida se ficou inativa; trocar de empresa exige uma empresa ativa. Nome, laboratório, descrição, escopo e contato obedecem aos limites do contrato. Contato próprio é normalizado no salvamento, enquanto orçamentos anteriores preservam o contato da empresa até serem editados.

Ao mudar de `PendingAcceptance` para `InProgress`, a atualização mantém uma etapa técnica resolvida quando já existem ensaios. Sem ensaios, conserva `Aprovado pelo cliente`, `Aguardando entrega da amostra`, `Recebido` ou `Protocolado`; outras etapas iniciais passam a `Recebido`. A criação de um ensaio inicia o andamento técnico sem alterar o status comercial.

É obrigatório informar ao menos um item, com quantidade inteira positiva, preço unitário não negativo de até duas casas decimais e norma opcional. O backend calcula `totalPrice` como a soma de `sampleCount * unitPrice`; o valor enviado deve coincidir com a soma sem arredondamento. Dados inválidos retornam 400 e código duplicado retorna 409.

O documento é opcional e limitado a 50 MiB; a API valida extensão `.pdf` e assinatura `%PDF-`. Na atualização, omitir a parte `document` preserva o arquivo atual; enviar outro PDF o substitui. Dados do orçamento, itens e documento são gravados na mesma transação. Arquivo acima do limite retorna 413. As consultas do cliente permanecem disponíveis em `/api/client/quotes` com seu contrato atual.

`Orcamento` e `DocumentoOrcamento` representam os recursos nas tabelas `orcamentos` e `documentos_orcamentos`. A fonte de conexão pertence à configuração da aplicação; os nomes dos recursos não dependem do ambiente ou backend escolhido. O campo público `origin` das empresas conserva o contrato existente e não concede acesso a dados externos.

Nas respostas de orçamento, `id` identifica o orçamento persistido no Spring. O campo `opportunityId` pertence ao contrato Salesforce da API C# e não é retornado pelo Spring.

### Consulta técnica de orçamentos

Técnicos podem listar propostas em `GET /api/technician/quotes` e consultar os detalhes em `GET /api/technician/quotes/{id}`. As duas rotas exigem perfil TECNICO e permitem apenas consulta.

A lista inclui somente propostas em `InProgress` ou `Finished`; pendentes, recusadas e canceladas ficam fora antes da busca, dos filtros, da contagem e da paginação. O mesmo conjunto comercial limita as opções em `GET /api/tests/references/quotes`. Para uma proposta elegível sem execuções, `technicalStatus` retorna `not-started`.

A listagem aceita `search`, `status`, `from`, `to`, `page` e `pageSize`. A busca encontra trechos do código, nome da proposta ou razão social, sem diferenciar maiúsculas de minúsculas. As datas usam `YYYY-MM-DD` e incluem o dia inteiro no fuso `America/Sao_Paulo`; é possível informar só o início ou só o fim do período. A página inicial é 1, com 20 itens por padrão e limite de 100 por página. Os resultados vêm dos mais recentes para os mais antigos, com desempate pelo maior ID.

A resposta contém `items`, `page`, `pageSize` e `totalItems`. Cada proposta traz `id`, `code`, `name`, `companyName`, `createdDate` e `technicalStatus`. O detalhe acrescenta `description`, `laboratoryName`, `scope` e itens com `testType`, `sampleCount` e `standard`. A consulta técnica não retorna valores, contatos comerciais, etapa, status comercial ou PDF. As rotas administrativas `/api/quotes` e seus documentos exigem perfil ADMIN.

O status técnico acompanha o andamento das execuções vinculadas à proposta. Ele é calculado antes da paginação, com a mesma regra na lista e no detalhe. Quando há execuções ativas, as canceladas ficam fora do cálculo e prevalece a etapa menos avançada:

| Status técnico | Condição |
| --- | --- |
| `incomplete` | Há uma execução ativa incompleta. |
| `not-started` | A proposta é elegível e ainda não tem execuções vinculadas. |
| `complete` | Todas chegaram pelo menos a completo, mas alguma ainda não foi validada. |
| `validated` | Todas chegaram pelo menos a validado, mas ainda falta concluir a publicação de algum relatório. Inclui execuções em `finalized` e `drafting-report`. |
| `report-published` | Todas as execuções ativas estão publicadas, com referência de relatório preenchida e data em `publishedAt`. |
| `cancelled` | Todas as execuções foram canceladas. |

Para proposta fora das etapas comerciais elegíveis, `technicalStatus` pode ser nulo quando não há execuções. Uma execução marcada como publicada precisa ter referência e data do relatório; sem esses dados, a consulta falha em vez de classificá-la como validada.

Os itens do orçamento descrevem os serviços propostos; as execuções registram seu andamento técnico separadamente. Cada execução pode guardar a referência e a data de publicação de um relatório. O cadastro completo de laudos e seu histórico ainda não estão implementados.

Limites: empresa - razão social 200, endereço 255, cidade 120, UF brasileira com 2 caracteres, nome do contato 150, telefone 30, e-mail 254. CNPJ numérico com 14 dígitos e dígitos verificadores, aceitando máscara, é normalizado e único. Cliente - nome 150, e-mail 254, telefone 30, cargo 120; senha com até 72 caracteres e 72 bytes UTF-8, sem trim. CNPJ alfanumérico não é suportado.

### Dados de demonstração

Para carregar os exemplos, configure `DEMO_SEED_ENABLED=true` e reinicie o Backend Lab. Em um banco vazio, o seed cria:

- 6 empresas, 6 técnicos e 7 clientes;
- 42 orçamentos com os cinco status comerciais e 60 itens de serviço;
- 23 execuções com exemplos de resultados, pendências, cancelamentos, validação e publicação;
- 21 amostras vinculadas aos orçamentos e dois instrumentos laboratoriais;
- 14 PDFs de proposta, incluindo um documento Lactec e 13 propostas geradas e persistidas para os exemplos do acompanhamento do cliente.

Quatro propostas não têm laboratório, escopo, contato próprio ou itens, para demonstrar a consulta de registros antigos. A carteira `DEMO-PIPE-2026` cobre todas as etapas apresentadas no Portal do Cliente, além de cancelamento e recusa, e está ligada às duas empresas ativas da conta `demo.cliente.mariana@example.test`. A interface só apresenta a proposta a partir de `Em negociação`; os PDFs persistidos no seed começam nessa etapa. As execuções têm andamento próprio, separado do status comercial; o exemplo publicado possui referência e data do laudo, mas o arquivo do laudo ainda não é armazenado pelo sistema. As contas e empresas são fictícias; os CNPJs passam pela validação e os novos e-mails usam `example.test`.

As contas adicionadas usam a senha de desenvolvimento `Demo@12345`. Exemplos: `demo.tecnico.helena@example.test` e `demo.cliente.mariana@example.test`. Contas Lactec novas usam a senha `lactec`; senhas de contas existentes não são redefinidas. A senha de administrador é definida separadamente pelas variáveis `AUTH_SEED_ADMIN_*`.

O seed fica desativado por padrão e deve ser usado apenas no desenvolvimento local. Cada versão aplicada é registrada em `demo_seed_runs`, na mesma transação dos dados e dos documentos. Reiniciar o backend não reaplica versões já registradas, preservando edições e exclusões. A carga também corrige os status antigos de quatro propostas de demonstração, conferindo o código e o valor anterior antes de alterá-los. Não há sincronização com outras fontes.

### Status técnico de execução

`execucoes_ensaios.status` persiste os sete códigos técnicos do Apêndice E (`incomplete`, `complete`, `validated`, `finalized`, `drafting-report`, `report-published`, `cancelled`). A aplicação converte os códigos estritamente; valor desconhecido causa erro de leitura, sem status substituto.


## Integração futura de equipamentos e análises do cliente

As consultas de equipamentos e medições liberadas em `/api/client/**` estão previstas no
[plano de integração do Portal do Cliente](docs/integracao-portal-cliente.md). No Compose
regular, a API C# mantém login, orçamentos e relatórios publicados do Spring e responde
501 (`lab-integration-pending`) para equipamentos e indicadores enquanto essa integração
não estiver disponível.
