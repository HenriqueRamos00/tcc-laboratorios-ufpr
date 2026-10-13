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

Para criar a conta inicial do administrador na primeira execução local, preencha `AUTH_SEED_ADMIN_*` no arquivo `.env`. `AUTH_SEED_TECHNICIAN_*` permanece disponível para preparar um técnico legado de desenvolvimento; técnicos também podem ser cadastrados e mantidos pelo Portal Interno. As senhas são armazenadas com BCrypt.

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

O Portal do Cliente mantém suas chamadas à API C#. Os adapters C# para autenticar clientes e consultar orçamentos e documentos no Spring ainda não estão implementados. A conta de demonstração e os cadastros desta API não substituem o login de desenvolvimento do portal.

As consultas privadas `/api/client/quotes`, `/{id}` e `/{id}/document` conferem usuário, vínculos e situação das empresas atuais. O PDF está em tabela separada e seus bytes são consultados somente após a autorização. Empresa sem vínculo ativo retorna 403; orçamento/arquivo inexistente retorna 404. Cliente ativo sem empresas ativas pode autenticar e recebe lista vazia. Remover vínculo ou desativar usuário/empresa afeta o token anterior na próxima requisição.

`Orcamento` e `DocumentoOrcamento` representam os recursos nas tabelas `orcamentos` e `documentos_orcamentos`. A fonte de conexão pertence à configuração da aplicação; os nomes dos recursos não dependem do ambiente ou backend escolhido. O campo público `origin` das empresas conserva o contrato existente e não concede acesso a dados externos.

Nas respostas de orçamento, `id` identifica o orçamento persistido no Spring. O campo `opportunityId` pertence ao contrato Salesforce da API C# e não é retornado pelo Spring.

Limites: empresa - razão social 200, endereço 255, cidade 120, UF brasileira com 2 caracteres, nome do contato 150, telefone 30, e-mail 254. CNPJ numérico com 14 dígitos e dígitos verificadores, aceitando máscara, é normalizado e único. Cliente - nome 150, e-mail 254, telefone 30, cargo 120; senha com até 72 caracteres e 72 bytes UTF-8, sem trim. CNPJ alfanumérico não é suportado neste incremento.

Para carregar os dados de demonstração, configure `DEMO_SEED_ENABLED=true` e reinicie o Backend Lab. O seed SQL cria a conta local `lactec@lactec.com`, com senha `lactec`, uma empresa, um orçamento e o PDF estático do repositório. A flag fica desativada por padrão; habilite-a somente em ambiente local e não a use com banco de produção. Não há sincronização, associação automática com Lactec ou fallback para fontes externas.
