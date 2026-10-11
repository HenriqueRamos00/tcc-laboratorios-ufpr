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
| `backend-lab` - API Java, somente para desenvolvimento local | http://localhost:8080 |
| `postgres` | localhost:5432 |

O Portal Interno e o Portal do Cliente encaminham `/api` diretamente ao Backend Lab Spring. O C# continua disponível para suas integrações já existentes, mas não intermedeia essas operações web. O aplicativo Android mantém a integração anterior pela API C#; sua adaptação ao fluxo local não faz parte desta implementação.

Na execução local, a API Java é publicada em `127.0.0.1:8080` para inspeção e desenvolvimento. Em implantação, mantenha o Backend Lab e o PostgreSQL sem publicação externa.

O `.env` da raiz configura o Backend Lab, incluindo segredo e validade do JWT. `backend-portal-web/.env` continua disponível para configurações próprias da API C#, como Salesforce. Os arquivos Compose de cada subpasta continuam disponíveis para execução isolada.

Configure no `.env` da raiz `JWT_SECRET` (mínimo de 32 bytes) e `JWT_EXPIRATION_SECONDS` (padrão de 3600 segundos). O Spring valida a assinatura e a validade do token e consulta a situação e o perfil atuais do usuário para autorizar as rotas protegidas.

Antes de iniciar o Compose da raiz, encerre os serviços das subpastas que estiverem usando as mesmas portas.

### Selecionar serviços

O Compose inicia também as dependências do serviço escolhido:

```bash
docker compose up -d --build frontend-interno  # Portal Interno, API Java e banco
docker compose up -d --build frontend          # Portal do Cliente, API Java e banco
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

O ADMIN mantém empresas e clientes em `/admin/empresas` e `/admin/clientes` no Portal Interno. O cadastro de cliente define a senha inicial e uma ou mais empresas ativas. O Portal Interno acessa diretamente o Spring para login e gestão; o Portal do Cliente usa `POST /api/auth/login` do Spring para autenticação local e `/api/client/quotes` para orçamentos. O aplicativo Android mantém o contrato anterior com a API C# e não foi adaptado neste incremento. O Java armazena hashes BCrypt e consulta os vínculos atuais para autorizar orçamentos e PDFs do Portal do Cliente. A URL de autenticação do Portal do Cliente é configurável separadamente para permitir a futura definição do contrato Lactec; até essa definição, o padrão é o Spring local. Não se exige cadastro no AutoLAB.

Para carregar os dados de demonstração no Backend Lab, configure `DEMO_SEED_ENABLED=true` no `.env` local e reinicie a API. O seed SQL cria a empresa, a conta `lactec@lactec.com` (senha `lactec`), um orçamento e seu PDF estático versionado. A flag fica desativada por padrão; habilite-a somente em ambiente local.
