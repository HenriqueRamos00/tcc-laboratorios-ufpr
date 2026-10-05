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

Os três consumidores usam a API REST C# como entrada comum. O Portal Interno encaminha `/api` para `portal-api`; a API C# valida o JWT interno e encaminha as cinco operações de autenticação e técnicos ao Backend Lab pela rede privada do Compose. O cliente e o Android usam o mesmo contrato público do C#. O Portal do Cliente usa `http://localhost:5125` no navegador; suas portas `4200` e `5125` seguem os endereços e o CORS atuais do código.

Na execução local, a API Java também é publicada em `127.0.0.1:8080` para inspeção e desenvolvimento. Os frontends não devem apontar para essa porta. Em implantação, mantenha o Backend Lab e o PostgreSQL sem publicação externa.

O `.env` da raiz é a origem única dos valores compartilhados entre C# e Java neste Compose, incluindo segredo, emissor, audiência e tolerância do JWT. `backend-portal-web/.env` continua disponível para configurações próprias da API, como Salesforce. Os arquivos Compose de cada subpasta continuam disponíveis para execução isolada.

Para coordenar a integração interna, ajuste no `.env` da raiz `JWT_SECRET` (mínimo de 32 bytes), `JWT_EXPIRATION_SECONDS` (Java, padrão de 3600 segundos), `JWT_ISSUER` (padrão `tcc-backend-lab`), `JWT_AUDIENCE` (padrão `tcc-portal-api-internal`) e `JWT_CLOCK_SKEW_SECONDS` (padrão de 30 segundos, máximo de 60). `BACKEND_LAB_TIMEOUT_SECONDS` define o timeout do adapter C# (padrão de 10 segundos, permitido entre 1 e 60). Na execução sem Compose, configure a mesma chave, emissor, audiência e tolerância nos dois processos; o Java aceita `JWT_EXPIRATION_SECONDS` e o C# usa `BackendLab__BaseUrl` para o destino HTTP, por padrão `http://localhost:8080`. Em produção, use HTTPS nesse destino.

Antes de iniciar o Compose da raiz, encerre os serviços das subpastas que estiverem usando as mesmas portas.

### Selecionar serviços

O Compose inicia também as dependências do serviço escolhido:

```bash
docker compose up -d --build frontend-interno  # Portal Interno, API C#, API Java e banco
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
