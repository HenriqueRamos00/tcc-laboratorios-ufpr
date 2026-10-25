# tcc-laboratorios-ufpr

TCC Portal Web Laboratórios.

O [Backend Auth](backend-auth/README.md) é um serviço Spring Boot independente, com SQL Server, login do portal sem token e operações administrativas com Bearer tokens opacos, sem JWT. O primeiro administrador é cadastrado por um link gerado nos logs e administra somente esse serviço, com permissões independentes do Portal Web. O módulo tem seu próprio Compose; o frontend de administração será feito separadamente. A API C# usa esse serviço quando `Portal__DataSource=lactec` e emite o JWT do Portal do Cliente. Veja a [configuração da integração](backend-portal-web/README.md#configuração-e-execução-em-lactec).

## Demonstração com Backend Auth

Na raiz, com Docker Engine e Compose v2, sem configurar `.env`:

```bash
docker compose -f compose.auth.yaml up -d --build
```

Abra http://localhost:4200. O Compose inicia SQL Server, Backend Auth
(http://localhost:8081/swagger-ui.html), API C# (http://localhost:5125) e frontend.
Não inicia Backend Lab nem Portal Interno; encerre outros stacks que usem essas portas.

| Conta | Senha | Acesso |
| --- | --- | --- |
| `lactec@lactec.com` | `lactec@lactec.com` | Administrador do Backend Auth, pelo Swagger/API |
| `client1@lactec.com` | `client1@lactec.com` | EMPRESA DE ENERGIA: nove orçamentos, quatro equipamentos, nove laudos |
| `client2@lactec.com` | `client2@lactec.com` | DISTRIBUIDORA DE ENERGIA: nove orçamentos, dois equipamentos, dois laudos |

O seed cria o serviço `portal-web` e seu token de integração automaticamente. O portal
autentica no Auth, emite JWT e filtra orçamentos, PDFs e ensaios pelo CNPJ da conta.
As medidas e séries laboratoriais existentes são preservadas. O administrador não
tem acesso de cliente ao portal. Veja os [detalhes do demo](backend-auth/README.md#seed-de-demonstração).
Os relatórios de orçamento já simulados no frontend também são separados: quatro
para `client1` e cinco para `client2`, conforme seus orçamentos autorizados.

As senhas e chaves padrão são públicas e exclusivas deste ambiente local. O `.env`
da raiz permite substituir `SQLSERVER_SA_PASSWORD`, `BACKEND_AUTH_SERVICE_TOKEN`
e `PORTAL_JWT_SIGNING_KEY`; nenhum desses valores é enviado ao frontend.
O SQL Server fica somente na rede interna e usa o volume separado
`tcc-auth-demo_auth-sqlserver-data`. Reiniciar preserva senhas, estado e vínculos
editados; o seed apenas cria registros ausentes e renova seu token se estiver expirado.

```bash
docker compose -f compose.auth.yaml down     # preserva o banco
docker compose -f compose.auth.yaml down -v  # apaga o banco demo e os volumes do frontend
docker compose -f compose.auth.yaml up -d --build
```

Ao mudar a senha SQL depois do primeiro início, mantenha a senha já usada pelo volume
ou descarte somente os dados demo com o comando acima. O seed fica desativado por
padrão fora deste Compose. O arquivo é independente; não deve ser combinado com `compose.yaml`.

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

O Backend Lab também oferece autenticação de clientes em `POST /api/auth/login`, consulta de orçamentos em `/api/client/quotes` e download dos respectivos documentos. Com `Portal__DataSource=backend-lab`, o C# adapta o login em `POST /auth/login` e preserva o token Spring. Com `Portal__DataSource=lactec`, autentica no Backend Auth com um token de serviço restrito a `portal-web`, emite JWT próprio (15 minutos) e protege `/api/**` e `/auth/me`. As fontes são exclusivas e não há fallback. Angular guarda o JWT em localStorage, envia Bearer para o C# e restaura o perfil. Em `lactec`, os orçamentos são filtrados pelos CNPJs das empresas no JWT, incluindo detalhe e PDF; clientes sem empresas recebem lista vazia. `Portal__UseDemoData=true` seleciona os orçamentos fictícios e resolve a empresa dos ensaios embutidos pelo CNPJ; a integração real com o AutoLAB permanece para outra etapa.

Para testar o Portal do Cliente com Backend Auth, use `docker compose -f compose.auth.yaml up -d --build`. Para integração com o Salesforce real, siga a [configuração do C#](backend-portal-web/README.md#configuração-e-execução-em-lactec) e mantenha `Portal__UseDemoData=false`.

Para carregar os dados de demonstração do Backend Lab, configure `DEMO_SEED_ENABLED=true` no `.env` local e reinicie a API. O seed SQL cria uma empresa, a conta Spring `lactec@lactec.com` (senha `lactec`), um orçamento e seu PDF estático versionado. A flag fica desativada por padrão e deve ser habilitada somente em ambiente local.
