# Portal Interno Lactec

Aplicação Angular independente para os perfis Administrador e Técnico.

## Desenvolvimento local

Requisitos: Node.js 24 ou superior e npm 10 ou superior. Com asdf, o arquivo
`.tool-versions` seleciona a versão do Node.

```bash
npm ci
npm start
```

A aplicação fica em <http://localhost:4201> e encaminha `/api` diretamente para
a API Spring em `http://localhost:8080`. Para usar outro endereço de API:

```bash
API_PROXY_TARGET=http://localhost:8080 npm start
```

## Docker

Com o Backend Lab Spring publicado na porta `8080`, configure o ambiente e
inicie a versão de desenvolvimento do Portal Interno:

```bash
cp .env.example .env
docker compose up --build -d
```

A aplicação fica em <http://localhost:4201>. Para iniciar a versão de produção:

```bash
docker compose --profile prod up --build -d web-prod
```

A versão de produção fica em <http://localhost:8081>. O Nginx entrega os arquivos
do Angular ao navegador e encaminha `/api` ao backend pelo mesmo endereço,
evitando problemas de CORS. O destino da API pode ser alterado em `.env` com
`API_PROXY_TARGET`, por exemplo:

```dotenv
API_PROXY_TARGET=http://host.docker.internal:8080
```

O Compose desta aplicação inicia apenas o Portal Interno. O Backend Lab deve
estar disponível separadamente na porta publicada `8080`; dentro do contêiner,
`host.docker.internal` aponta para o host e o proxy encaminha as chamadas
diretamente à API Spring.

Se o Spring estiver no Compose da raiz ou no Compose de `backend-lab`, a porta
HTTP é publicada em loopback por padrão. No Linux, `host.docker.internal` usa o
endereço da bridge Docker e precisa alcançar essa porta. Inicie o backend com
publicação nesse endereço:

```bash
# Na raiz do repositório:
APP_BIND_ADDRESS=0.0.0.0 docker compose up -d --build backend-lab
# Ou, dentro de backend-lab/:
APP_BIND_ADDRESS=0.0.0.0 docker compose up -d --build
```

Essa opção publica a API nas interfaces do host. Use-a no ambiente de
desenvolvimento em que os containers separados precisam acessar o backend.
No Compose integrado da raiz, o Portal Interno usa `http://backend-lab:8080`
pela rede Docker e funciona com a publicação padrão em loopback.

O administrador inicial é configurado pelo backend usando `AUTH_SEED_ADMIN_*`.
`AUTH_SEED_TECHNICIAN_*` pode preparar um técnico de desenvolvimento. Depois de entrar no
Portal Interno, a gestão de técnicos está disponível em **Técnicos** no menu
administrativo.
