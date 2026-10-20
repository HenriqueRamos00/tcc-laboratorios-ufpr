# tcc-laboratorios-ufpr

TCC Portal Web Laboratórios.

## Docker Compose na raiz

Requisitos: Docker Engine e Docker Compose v2. Os comandos abaixo são executados na raiz do repositório.

Copie a configuração e preencha `AUTH_SEED_ADMIN_PASSWORD` para criar o administrador do Portal Interno:

```bash
cp .env.example .env
docker compose up -d --build
```

Esse comando sobe os dois portais de desenvolvimento, as duas APIs e o PostgreSQL. Os portais usam hot reload.

| Serviço | Endereço |
| --- | --- |
| `frontend` - Portal do Cliente | http://localhost:4200 |
| `frontend-interno` - Portal Interno | http://localhost:4201 |
| `portal-api` - API C# | http://localhost:5125 |
| `backend-lab` - API Spring do Portal Interno | http://localhost:8080 |
| `postgres` | localhost:5432 |

O Portal Interno encaminha `/api` diretamente para `backend-lab` pela rede do Compose. O Spring autentica Administradores e Técnicos, autoriza as operações e persiste os dados no PostgreSQL.

O Portal do Cliente e o Android usam a API C#. O Portal do Cliente acessa `http://localhost:5125` no navegador.

Na execução local, a API Spring é publicada em `127.0.0.1:8080` por padrão. `APP_BIND_ADDRESS` configura a interface de publicação; use `0.0.0.0` quando um Portal Interno em Compose separado acessar a API por `host.docker.internal`. O Compose integrado usa a rede Docker e dispensa esse ajuste. No navegador, o Portal Interno usa a origem da própria aplicação e seu proxy `/api`. Em implantação, disponibilize esse proxy por HTTPS e mantenha o PostgreSQL na rede privada.

O `.env` da raiz configura o PostgreSQL, o seed e o JWT do Spring. `backend-portal-web/.env` continua disponível para configurações próprias do C#, como Salesforce. Os Compose das subpastas permitem execução isolada.

Configure `JWT_SECRET` (mínimo de 32 bytes UTF-8) e `JWT_EXPIRATION_SECONDS` (padrão de 3600 segundos). O Spring valida a assinatura e a validade do token e consulta a situação e o perfil atuais do usuário para autorizar as rotas protegidas. Alterar o segredo exige novo login.

Antes de iniciar o Compose da raiz, encerre os serviços das subpastas que estiverem usando as mesmas portas.

### Selecionar serviços

O Compose inicia também as dependências do serviço escolhido:

```bash
docker compose up -d --build frontend-interno  # Portal Interno, API Spring e banco
docker compose up -d --build frontend          # Portal do Cliente e API C#
docker compose up -d --build backend-lab       # Somente API Java e banco
docker compose up -d --build portal-api        # Somente API C#, sem exigir Java e PostgreSQL
docker compose up -d postgres                 # Somente banco
```

Para iniciar vários serviços, informe seus nomes no mesmo comando.

### Android

O perfil `android` acrescenta o ambiente de build e ADB. O aplicativo é instalado em um aparelho; o Compose não inicia um emulador.

```bash
docker compose --profile android up -d --build  # Todos os serviços, incluindo Android
docker compose up -d --build app-android        # Somente ambiente Android
docker compose exec app-android ./gradlew assembleDebug
```

Configure `ADB_DEVICE` e, se necessário, `HOST_UID`/`HOST_GID` no `.env`. O fluxo de instalação no aparelho está no [README do Android](app-android/README.md).

### Logs e dados

```bash
docker compose ps
docker compose logs -f portal-api backend-lab frontend-interno
docker compose --profile android down
```

O comando `down` preserva os volumes. O banco deste Compose usa `tcc-laboratorios-postgres`, separado do volume criado pelo Compose isolado do Backend Lab. Para reutilizar um banco existente, defina `LAB_POSTGRES_VOLUME` com o nome desse volume e use suas credenciais no `.env`; encerre o Compose anterior antes de iniciar o da raiz.

Após mudar as dependências dos frontends, recrie seus volumes anônimos:

```bash
docker compose up -d --build --renew-anon-volumes frontend frontend-interno
```

## Empresas e clientes

O ADMIN mantém empresas e clientes em `/admin/empresas` e `/admin/clientes` no Portal Interno, que acessa diretamente a API Spring. O cadastro de cliente define a senha inicial e os vínculos com uma ou mais empresas ativas. O Spring armazena hashes BCrypt, aplica as regras de cadastro e preserva o histórico ao desativar registros.

O Backend Lab também oferece autenticação de clientes em `POST /api/auth/login`, consulta de orçamentos em `/api/client/quotes` e download dos respectivos documentos. A API C# adapta o login em `POST /auth/login`, encaminhando as credenciais ao Spring e preservando o token emitido por ele. O Portal do Cliente conserva o login de desenvolvimento; a compatibilidade entre os claims Spring (`role`, `userId` e `sub`) e o contrato usado pelo portal (`roles` e `nome`) permanece pendente. Os adapters C# para orçamentos e documentos do Spring também estão pendentes. A fonte `lactec` preserva as integrações atuais e permite iniciar a API C# sem Spring; nessa fonte, `POST /auth/login` retorna 501 enquanto o contrato de autenticação Lactec estiver pendente, sem fallback automático.

Para carregar os dados de demonstração do Backend Lab, configure `DEMO_SEED_ENABLED=true` no `.env` local e reinicie a API. O seed SQL cria uma empresa, a conta Spring `lactec@lactec.com` (senha `lactec`), um orçamento e seu PDF estático versionado. A flag fica desativada por padrão e deve ser habilitada somente em ambiente local.

## Auth Service

O `auth-service` é o serviço de autenticação e autorização independente do projeto. Ele mantém contas do portal-web, empresas, vínculos de usuários com empresas e chaves de integração para serviços, além de emitir tokens OAuth2/OIDC para o cliente `portal-web`.

Ele possui um Compose próprio em `auth-service/`, com SQL Server, a API e um painel administrativo Angular exclusivo para administradores. O Compose da raiz não foi alterado.

Consulte o [README do Auth Service](auth-service/README.md) para configuração do banco, chave de assinatura, criação segura do primeiro administrador, execução local e uso das API keys.
