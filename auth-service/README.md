# Auth Service

Serviço de autenticação e autorização do projeto. Ele mantém contas do portal-web,
empresas, vínculos de usuários com empresas e chaves de integração, além de emitir
tokens OAuth2/OIDC para o cliente `portal-web`.

## Componentes

| Componente | Caminho | Porta padrão |
|---|---|---|
| API Spring | `backend/auth` | `9000` |
| Painel administrativo Angular | `frontend` | `4202` |
| SQL Server | `compose.yaml` | `1433` |

O painel é exclusivo para contas com perfil `ADMIN`. O portal-web continua usando o
fluxo OAuth2 authorization code + PKCE já existente.

## Pré-requisitos

- Java 21 via asdf (`auth-service/.tool-versions`)
- Node.js 24 via asdf
- Docker e Docker Compose
- `keytool` (incluído no JDK)

```bash
asdf install
```

## Primeira execução com Docker Compose

```bash
cd auth-service
cp .env.example .env
```

1. Gere a chave de assinatura PKCS12:

```bash
./scripts/generate-signing-keystore.sh
```

O script usa `AUTH_KEY_STORE_PASSWORD` (padrão `change-me`). Se alterar a senha no
`.env`, execute-o com a mesma variável:

```bash
AUTH_KEY_STORE_PASSWORD='sua-senha' ./scripts/generate-signing-keystore.sh
```

2. Defina uma senha forte em `AUTH_DB_PASSWORD` e um segredo com pelo menos 32
   caracteres em `AUTH_PORTAL_CLIENT_SECRET`.

3. Gere o token de configuração inicial e coloque em `AUTH_SETUP_TOKEN`:

```bash
openssl rand -base64 48
```

4. Suba os serviços:

```bash
docker compose up -d --build
```

O Compose cria o banco `auth` e aplica `backend/auth/src/main/resources/schema.sql`
em um volume novo. O painel fica em <http://localhost:4202>.

5. Abra o painel, acesse `/setup`, informe o `AUTH_SETUP_TOKEN`, o nome, o e-mail e
   uma senha com pelo menos 15 caracteres. A configuração inicial é encerrada após
   a criação do primeiro `ADMIN`.

6. Remova `AUTH_SETUP_TOKEN` do `.env` depois da configuração. A aplicação já
   bloqueia novas configurações quando existe um administrador, mas o segredo não
   deve permanecer no ambiente.

## Execução local sem Docker

A aplicação espera um SQL Server acessível e as variáveis de ambiente do
`.env.example`. Ajuste `AUTH_DB_URL` para o seu servidor e `AUTH_KEY_STORE` para o
caminho do keystore.

```bash
cd auth-service/backend/auth
sh ./mvnw spring-boot:run
```

Em outro terminal:

```bash
cd auth-service/frontend
npm ci
API_PROXY_TARGET=http://localhost:9000 npm start
```

O proxy do Angular encaminha `/api` para o auth-service, mantendo a sessão e o
cookie CSRF na mesma origem.

## Schema e migração

Não há Flyway. `schema.sql` é o schema autoritativo para uma instalação nova. Em um
banco já existente da primeira versão do auth-service, aplique uma vez:

```bash
sqlcmd -S localhost -U sa -P "$AUTH_DB_PASSWORD" -C \
  -v AUTH_DB_NAME=auth -i database/upgrade-v2.sql
```

O script adiciona `role`, renomeia `username` para `name`, remove
`service`/`service_id` e cria `company`, `auth_user_company` e `api_key`.
Contas antigas passam a ser `USER`; o primeiro `ADMIN` é criado pelo fluxo de
setup.

## API administrativa

Todas as rotas abaixo usam sessão HttpOnly e exigem `ADMIN`, exceto as indicadas.

| Método | Rota | Descrição |
|---|---|---|
| `GET` | `/api/setup/status` | público; informa se a configuração inicial é necessária |
| `POST` | `/api/setup` | público, protegido por `X-Setup-Token`; cria o primeiro admin |
| `POST` | `/api/admin/auth/login` | login JSON, cria a sessão |
| `GET` | `/api/admin/auth/session` | sessão atual |
| `GET` | `/api/admin/auth/csrf` | devolve o token CSRF da sessão |
| `POST` | `/api/admin/auth/logout` | encerra a sessão |
| `GET/POST` | `/api/admin/users` | lista/cria usuários |
| `PUT/DELETE` | `/api/admin/users/{id}` | edita/exclui usuário |
| `PATCH` | `/api/admin/users/{id}/status` | ativa/desativa usuário |
| `GET/POST` | `/api/admin/companies` | lista/cria empresas |
| `PUT/DELETE` | `/api/admin/companies/{id}` | edita/exclui empresa |
| `GET/POST` | `/api/admin/api-keys` | lista/cria chaves de integração |
| `PATCH` | `/api/admin/api-keys/{id}/status` | ativa/revoga chave |
| `DELETE` | `/api/admin/api-keys/{id}` | exclui chave |

Empresas com usuários vinculados não podem ser excluídas. Usuários com perfil
`USER` precisam de pelo menos uma empresa. O sistema impede excluir/desativar o
último administrador e impede que um administrador exclua ou desative a própria
conta.

## API de integração

Serviços autorizados chamam `/api/integrations/**` com o cabeçalho:

```http
X-API-Key: auth_...
```

A chave completa é exibida somente na criação. O banco guarda apenas o prefixo e o
hash SHA-256. A rota inicial é:

```http
GET /api/integrations/me
```

```json
{
  "id": "...",
  "name": "Serviço de orçamentos",
  "prefix": "auth_abc1234",
  "expiresAt": null
}
```

Chaves revogadas, expiradas ou inexistentes retornam `401`. As rotas OAuth2/OIDC
continuam usando a autenticação padrão de clientes OAuth2; a API key não substitui
o client secret do `portal-web`.

## Tokens do portal-web

O access token emitido para o cliente `portal-web` contém:

```json
{
  "sub": "<id do usuário>",
  "aud": ["portal-api"],
  "companies": [
    { "id": "...", "name": "...", "cnpj": "..." }
  ]
}
```

As empresas são recarregadas na renovação do token. Desativar uma conta impede
novos logins e a renovação de tokens. Tokens de acesso já emitidos expiram em até
10 minutos.

## Segurança

- Senhas usam PBKDF2 com 600.000 iterações.
- O primeiro admin só pode ser criado enquanto não existe nenhum `ADMIN` e o token
  de setup estiver configurado.
- A sessão administrativa é um cookie `HttpOnly`, `SameSite=Lax`, com expiração
  configurável. O token CSRF é entregue por `/api/admin/auth/csrf` e enviado no
  cabeçalho `X-CSRF-TOKEN`; ele fica apenas em memória no Angular e não é
  persistido em `localStorage`/`sessionStorage`.
- Chaves de API são aleatórias, armazenadas como hash e exibidas uma única vez.
- A API key é aceita apenas no namespace `/api/integrations/**`.
- Em produção, use HTTPS, `AUTH_COOKIE_SECURE=true` e um certificado válido para o
  SQL Server.
