# tcc-laboratorios-ufpr

TCC Portal Web Laboratórios.

O [Backend Auth2](backend-auth2/README.md) é o serviço Spring Boot de autenticação usado pelo Compose de demonstração, com Oracle, login de clientes autenticado por token de aplicação e operações administrativas com Bearer tokens opacos. A API C# usa esse serviço quando `Portal__DataSource=lactec` e emite o JWT do Portal do Cliente. O administrador do Auth2 tem permissões independentes do Portal Web. A implementação anterior continua em [Backend Auth](backend-auth/README.md), com SQL Server e Compose próprio. Veja a [configuração da integração](backend-portal-web/README.md#configuração-e-execução-em-lactec).

## Demonstração com Backend Auth2

Na raiz, com Docker Engine e Compose v2, sem configurar `.env`:

```bash
docker compose -f compose.auth.yaml up -d --build --remove-orphans
```

Abra http://localhost:4200. O Compose inicia Oracle para o Auth2, migrações Flyway,
Backend Auth2 (http://localhost:8081), SQL Server com AutoLAB local,
API C# (http://localhost:5125) e frontend.
Não inicia Backend Lab nem Portal Interno; encerre outros stacks que usem essas portas.
O Oracle pode levar alguns minutos no primeiro início. A prontidão do Auth2 está em
http://localhost:8081/health/readiness; o Swagger fica desativado por padrão.

| Conta | Senha | Acesso |
| --- | --- | --- |
| `lactec@lactec.com` | `Lactec!7uQ2mN9xR4pK` | Administrador do Backend Auth2, pela API |
| `client1@lactec.com` | `Cliente1!6vT9qL2nW8sD` | EMPRESA DE ENERGIA / `30960572000183`: nove orçamentos, seis equipamentos e onze laudos do AutoLAB demo |
| `client2@lactec.com` | `Cliente2!3rK7wP5mH9zF` | DISTRIBUIDORA DE ENERGIA / `11222333000181`: nove orçamentos, seis equipamentos e onze laudos do AutoLAB demo |

O seed cria o serviço `portal-web` e seu token de integração automaticamente. O portal
autentica no Auth, emite JWT e filtra orçamentos e propostas PDF pelo CNPJ da conta.
Equipamentos e análises do AutoLAB usam o escopo global explícito do demo (`LabResults__Scope=all`).
As medidas e séries laboratoriais existentes são preservadas. O administrador não
tem acesso de cliente ao portal. Os clientes já nascem ativos e com e-mail confirmado.
O demo sobe sem SMTP; confirmação de novos cadastros e recuperação por e-mail exigem
configurar o envio no Auth2. Veja os [detalhes do Auth2](backend-auth2/README.md).
Os relatórios e PDFs laboratoriais vêm do banco AutoLAB local. A consulta por orçamento
resolve primeiro o orçamento autorizado e usa seu código para selecionar os relatórios.
As propostas comerciais são geradas pelo C# com os dados do orçamento e o layout Lactec.

As senhas e chaves padrão são públicas e exclusivas deste ambiente local. O `.env`
da raiz permite substituir `SQLSERVER_SA_PASSWORD`, `AUTH_ORACLE_PASSWORD`, `AUTH_SCHEMA_OWNER_PASSWORD`,
`AUTH_DATASOURCE_PASSWORD`, `AUTH_DEMO_ADMIN_PASSWORD`, `AUTH_DEMO_CLIENT1_PASSWORD`,
`AUTH_DEMO_CLIENT2_PASSWORD`, `BACKEND_AUTH_SERVICE_TOKEN` e `PORTAL_JWT_SIGNING_KEY`.
Credenciais de banco e integração não são enviadas ao frontend. As senhas de pessoas
são armazenadas com Argon2id.
Os dois bancos ficam somente na rede interna. O Oracle usa
`tcc-auth-demo_auth-oracle-data`; o AutoLAB usa `tcc-auth-demo_auth-sqlserver-data`.
Reiniciar preserva senhas, estado e vínculos editados; o seed apenas cria registros
ausentes e renova seu token se estiver expirado, sem desfazer uma revogação manual.
As variáveis de senha das contas são usadas somente na criação inicial de cada conta.

Ao migrar do Compose antigo, `--remove-orphans` remove o container do Backend Auth
anterior. O SQL Server e seu volume são reutilizados pelo AutoLAB; o banco legado
`backend_auth` é preservado e deixa de ser usado pela autenticação. O seed recria no
Oracle os dados demo versionados; alterações manuais do Auth antigo exigem exportação própria.
O `autolab-init` recarrega a massa sintética do banco `autolab` a cada execução;
as contas e permissões do Auth2 permanecem no Oracle.

```bash
docker compose -f compose.auth.yaml down     # preserva os dois bancos
docker compose -f compose.auth.yaml down -v  # apaga os dois bancos demo, o Auth SQL legado e os volumes do frontend
docker compose -f compose.auth.yaml up -d --build
```

Em volume SQL Server existente, mantenha `SQLSERVER_SA_PASSWORD` com a senha SA
já usada pelo banco. `AUTH_ORACLE_PASSWORD` configura a senha administrativa na criação do banco; mudar
esse valor não altera a senha de um volume existente. As senhas dos usuários do esquema
e da aplicação são sincronizadas pelo script de inicialização. O seed fica desativado por
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

O `compose.yaml` seleciona explicitamente o Backend Lab para login, orçamentos, etapas,
relatórios publicados e PDFs. Não exige AutoLAB nem SQL Server. Equipamentos e indicadores
aguardam os endpoints de cliente do Spring: suas rotas retornam 501 com o código
`lab-integration-pending`, e o portal informa a indisponibilidade. O plano está em
[Integração do Portal do Cliente](backend-lab/docs/integracao-portal-cliente.md).
O `compose.auth.yaml` seleciona Backend Auth2 com Oracle, orçamentos demonstrativos e fontes AutoLAB
SQL Server. Os dois arquivos configuram seus providers independentemente do `.env` compartilhado.
No stack Auth, a conexão AutoLAB de `backend-portal-web/.env` prevalece sobre os padrões
do banco demo local; a massa local é usada quando não há essa configuração.

Na execução local, a API Spring é publicada em `127.0.0.1:8080` por padrão. `APP_BIND_ADDRESS` configura a interface de publicação; use `0.0.0.0` quando um Portal Interno em Compose separado acessar a API por `host.docker.internal`. O Compose integrado usa a rede Docker e dispensa esse ajuste. No navegador, o Portal Interno usa a origem da própria aplicação e seu proxy `/api`. Em implantação, disponibilize esse proxy por HTTPS e mantenha o PostgreSQL na rede privada.

O `.env` da raiz configura o PostgreSQL, o seed e o JWT do Spring. `backend-portal-web/.env` continua disponível para configurações próprias do C#, como Salesforce. Os Compose das subpastas permitem execução isolada.

Configure `JWT_SECRET` (mínimo de 32 bytes UTF-8) e `JWT_EXPIRATION_SECONDS` (padrão de 3600 segundos). O Spring valida a assinatura e a validade do token e consulta a situação e o perfil atuais do usuário para autorizar as rotas protegidas. Alterar o segredo exige novo login.

Antes de iniciar o Compose da raiz, encerre os serviços das subpastas que estiverem usando as mesmas portas.

### Selecionar serviços

O Compose inicia também as dependências do serviço escolhido:

```bash
docker compose up -d --build frontend-interno  # Portal Interno, API Spring e banco
docker compose up -d --build frontend backend-lab # Portal do Cliente, API C#, Spring e banco
docker compose up -d --build backend-lab       # Somente API Java e banco
docker compose up -d --build portal-api        # Somente API C#; requer Backend Lab já acessível para login e consultas
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

O Backend Lab também oferece autenticação de clientes em `POST /api/auth/login`, consulta de orçamentos em `/api/client/quotes` e download dos respectivos documentos. Com `Portal__DataSource=backend-lab`, o C# adapta o login em `POST /auth/login` e preserva o token Spring. Com `Portal__DataSource=lactec`, autentica no Backend Auth com um token de serviço restrito a `portal-web`, emite JWT próprio (15 minutos) e protege `/api/**` e `/auth/me`. As fontes são exclusivas e não há fallback. Angular guarda o JWT em localStorage, envia Bearer para o C# e restaura o perfil. Em `lactec`, os orçamentos são filtrados pelos CNPJs das empresas no JWT, incluindo detalhe e PDF; clientes sem empresas recebem lista vazia. `Portal__UseDemoData=true` seleciona apenas os orçamentos fictícios. Em `compose.yaml`, os relatórios publicados vêm do Spring; equipamentos e indicadores ainda estão indisponíveis. Em `compose.auth.yaml`, equipamentos, ensaios e relatórios vêm do SQL Server AutoLAB local, com massa sintética gerada por `tools/gerar-massa-autolab.mjs`, exibida de forma generalizada (`LabResults__Scope=all`).

Para testar o Portal do Cliente com Backend Auth2, use `docker compose -f compose.auth.yaml up -d --build`. Para integração com o Salesforce real, siga a [configuração do C#](backend-portal-web/README.md#configuração-e-execução-em-lactec) e mantenha `Portal__UseDemoData=false`.

Para carregar os dados de demonstração do Backend Lab, configure `DEMO_SEED_ENABLED=true` no `.env` local e reinicie a API. O seed SQL cria uma empresa, a conta Spring `lactec@lactec.com` (senha `lactec`), um orçamento e seu PDF estático versionado. A flag fica desativada por padrão e deve ser habilitada somente em ambiente local.
