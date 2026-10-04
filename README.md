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
| `backend-lab` - API Java | http://localhost:8080 |
| `postgres` | localhost:5432 |

O Portal Interno encaminha `/api` ao Backend Lab pela rede do Compose. O Portal do Cliente usa `http://localhost:5125` no navegador; suas portas `4200` e `5125` seguem os endereços e o CORS atuais do código.

As variáveis do `.env` da raiz configuram este Compose. As integrações da API C# continuam usando `backend-portal-web/.env`, quando esse arquivo existir. Os arquivos Compose de cada subpasta continuam disponíveis para execução isolada.

Antes de iniciar o Compose da raiz, encerre os serviços das subpastas que estiverem usando as mesmas portas.

### Selecionar serviços

O Compose inicia também as dependências do serviço escolhido:

```bash
docker compose up -d --build frontend-interno  # Portal Interno, API Java e banco
docker compose up -d --build frontend          # Portal do Cliente e API C#
docker compose up -d --build backend-lab       # Somente API Java e banco
docker compose up -d --build portal-api        # Somente API C#
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
docker compose logs -f backend-lab frontend-interno
docker compose --profile android down
```

O comando `down` preserva os volumes. O banco deste Compose usa `tcc-laboratorios-postgres`, separado do volume criado pelo Compose isolado do Backend Lab. Para reutilizar um banco existente, defina `LAB_POSTGRES_VOLUME` com o nome desse volume e use suas credenciais no `.env`; encerre o Compose anterior antes de iniciar o da raiz.

Após mudar as dependências dos frontends, recrie seus volumes anônimos:

```bash
docker compose up -d --build --renew-anon-volumes frontend frontend-interno
```
