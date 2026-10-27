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

O Portal do Cliente acessa a API C#. No modo `backend-lab`, o C# encaminha o login para esta API Spring; os adapters de orçamentos e documentos ainda não estão implementados. Para entrar nesse modo, use uma conta de cliente ativa cadastrada no Backend Lab.

As consultas privadas `/api/client/quotes`, `/{id}` e `/{id}/document` conferem usuário, vínculos e situação das empresas atuais. O PDF está em tabela separada e seus bytes são consultados somente após a autorização. Empresa sem vínculo ativo retorna 403; orçamento/arquivo inexistente retorna 404. Cliente ativo sem empresas ativas pode autenticar e recebe lista vazia. Remover vínculo ou desativar usuário/empresa afeta o token anterior na próxima requisição.

### Consulta e manutenção administrativa de orçamentos

As rotas administrativas de `/api/quotes` são exclusivas de ADMIN. O parâmetro opcional `status` da listagem aceita somente os códigos comerciais `PendingAcceptance`, `InProgress`, `Finished`, `Refused` e `Cancelled`, definidos por `StatusComercialOrcamento`; valor desconhecido retorna 400. A atualização também exige um código comercial. O campo de etapa permanece textual e separado do status.

`GET /api/quotes` pesquisa parcialmente por código, nome, descrição ou razão social da empresa. A paginação começa em 1, usa `pageSize=20` por padrão e aceita de 1 a 100 itens por página. A resposta contém `items`, `page`, `pageSize` e `totalItems`, ordenada por data de criação decrescente e ID decrescente como desempate. Cada item contém `id`, `code`, `name`, `status`, `stage`, `description`, `companyName`, `externalContactName`, `externalContactEmail`, `totalPrice`, `createdDate` e `hasDocument`. A listagem não carrega os itens de serviço nem os bytes do PDF.

`GET /api/quotes/{id}` retorna os campos atuais e acrescenta `companyId`, `laboratoryName`, `scope` e `items`. Registros anteriores à manutenção têm laboratório e escopo nulos, itens vazios e usam como contato externo o contato da empresa. `GET /api/quotes/{id}/document` baixa o PDF existente em `application/pdf`; detalhe e documento retornam 404 quando o recurso não existe.

`POST /api/quotes` cria orçamento e retorna 201; `PUT /api/quotes/{id}` atualiza e retorna 200. Ambos aceitam JSON ou `multipart/form-data`, com parte `quote` em `application/json` e parte opcional `document` em PDF. O corpo usa `code`, `companyId`, `name`, `laboratoryName`, `description`, `scope`, `externalContactName`, `externalContactEmail`, `status`, `totalPrice` e `items`. Cada item contém `testType`, `sampleCount`, `standard` e `unitPrice`. O nome do laboratório é texto livre; os itens pertencem ao orçamento e não criam ensaios, amostras ou serviços independentes.

O código é obrigatório, tem até 80 caracteres, é aparado nas extremidades, mantém a caixa informada e é único. A criação exige empresa ativa, status `PendingAcceptance` e define a etapa `Negociação` e a data no servidor. A atualização preserva etapa e data e exige um dos cinco estados comerciais. O status é um enum no domínio e na persistência; valores armazenados fora dos cinco códigos impedem a leitura do orçamento e devem ser migrados antes da inicialização do serviço. A empresa original pode ser mantida se ficou inativa; trocar de empresa exige uma empresa ativa. Nome, laboratório, descrição, escopo e contato obedecem aos limites do contrato. Contato próprio é normalizado no salvamento, enquanto orçamentos anteriores preservam o contato da empresa até serem editados.

É obrigatório informar ao menos um item, com quantidade inteira positiva, preço unitário não negativo de até duas casas decimais e norma opcional. O backend calcula `totalPrice` como a soma de `sampleCount * unitPrice`; o valor enviado deve coincidir com a soma sem arredondamento. Dados inválidos retornam 400 e código duplicado retorna 409.

O documento é opcional e limitado a 50 MiB; a API valida extensão `.pdf` e assinatura `%PDF-`. Na atualização, omitir a parte `document` preserva o arquivo atual; enviar outro PDF o substitui. Dados do orçamento, itens e documento são gravados na mesma transação. Arquivo acima do limite retorna 413. As consultas do cliente permanecem disponíveis em `/api/client/quotes` com seu contrato atual.

`Orcamento` e `DocumentoOrcamento` representam os recursos nas tabelas `orcamentos` e `documentos_orcamentos`. A fonte de conexão pertence à configuração da aplicação; os nomes dos recursos não dependem do ambiente ou backend escolhido. O campo público `origin` das empresas conserva o contrato existente e não concede acesso a dados externos.

Nas respostas de orçamento, `id` identifica o orçamento persistido no Spring. O campo `opportunityId` pertence ao contrato Salesforce da API C# e não é retornado pelo Spring.

### Consulta técnica de orçamentos

Técnicos podem listar propostas em `GET /api/technician/quotes` e consultar os detalhes em `GET /api/technician/quotes/{id}`. As duas rotas exigem perfil TECNICO e permitem apenas consulta.

A listagem aceita `search`, `status`, `from`, `to`, `page` e `pageSize`. A busca encontra trechos do código, nome da proposta ou razão social, sem diferenciar maiúsculas de minúsculas. As datas usam `YYYY-MM-DD` e incluem o dia inteiro no fuso `America/Sao_Paulo`; é possível informar só o início ou só o fim do período. A página inicial é 1, com 20 itens por padrão e limite de 100 por página. Os resultados vêm dos mais recentes para os mais antigos, com desempate pelo maior ID.

A resposta contém `items`, `page`, `pageSize` e `totalItems`. Cada proposta traz `id`, `code`, `name`, `companyName`, `createdDate` e `technicalStatus`. O detalhe acrescenta `description`, `laboratoryName`, `scope` e itens com `testType`, `sampleCount` e `standard`. A consulta técnica não retorna valores, contatos comerciais, etapa, status comercial ou PDF. As rotas administrativas `/api/quotes` e seus documentos exigem perfil ADMIN.

O status técnico acompanha o andamento das execuções vinculadas à proposta. Ele é calculado antes da paginação, com a mesma regra na lista e no detalhe. Quando há execuções ativas, as canceladas ficam fora do cálculo e prevalece a etapa menos avançada:

| Status técnico | Condição |
| --- | --- |
| `incomplete` | Há uma execução ativa incompleta. |
| `complete` | Todas chegaram pelo menos a completo, mas alguma ainda não foi validada. |
| `validated` | Todas chegaram pelo menos a validado, mas ainda falta concluir a publicação de algum relatório. Inclui execuções em `finalized` e `drafting-report`. |
| `report-published` | Todas as execuções ativas estão publicadas, com referência de relatório preenchida e data em `publishedAt`. |
| `cancelled` | Todas as execuções foram canceladas. |

Sem execuções, `technicalStatus` é nulo. Uma execução marcada como publicada sem referência ou data do relatório ainda conta como `validated`.

Os itens do orçamento descrevem os serviços propostos; as execuções registram seu andamento técnico separadamente. Cada execução pode guardar a referência e a data de publicação de um relatório. O cadastro completo de laudos e seu histórico ainda não estão implementados.

Limites: empresa - razão social 200, endereço 255, cidade 120, UF brasileira com 2 caracteres, nome do contato 150, telefone 30, e-mail 254. CNPJ numérico com 14 dígitos e dígitos verificadores, aceitando máscara, é normalizado e único. Cliente - nome 150, e-mail 254, telefone 30, cargo 120; senha com até 72 caracteres e 72 bytes UTF-8, sem trim. CNPJ alfanumérico não é suportado.

### Dados de demonstração

Para carregar os exemplos, configure `DEMO_SEED_ENABLED=true` e reinicie o Backend Lab. Em um banco vazio, o seed cria:

- 6 empresas, 6 técnicos e 7 clientes;
- 26 orçamentos com os cinco status comerciais e 44 itens de serviço;
- 14 execuções técnicas com exemplos de pendências, cancelamentos, validação e publicação;
- um PDF de exemplo na proposta Lactec.

Quatro propostas não têm laboratório, escopo, contato próprio ou itens, para demonstrar a consulta de registros antigos. As execuções têm andamento próprio, separado do status comercial. As contas e empresas são fictícias; os CNPJs passam pela validação e os novos e-mails usam `example.test`.

As contas adicionadas usam a senha de desenvolvimento `Demo@12345`. Exemplos: `demo.tecnico.helena@example.test` e `demo.cliente.mariana@example.test`. Contas Lactec novas usam a senha `lactec`; senhas de contas existentes não são redefinidas. A senha de administrador é definida separadamente pelas variáveis `AUTH_SEED_ADMIN_*`.

O seed fica desativado por padrão e deve ser usado apenas no desenvolvimento local. Cada versão aplicada é registrada em `demo_seed_runs`, na mesma transação dos dados e do PDF. Reiniciar o backend não reaplica versões já registradas, preservando edições e exclusões. A carga também corrige os status antigos de quatro propostas de demonstração, conferindo o código e o valor anterior antes de alterá-los. Não há sincronização com outras fontes.
