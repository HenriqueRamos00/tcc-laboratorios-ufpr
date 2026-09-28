# Portal Interno Lactec

Aplicação Angular independente para os perfis Administrador e Técnico.

## Desenvolvimento local

Requisitos: Node.js 24 ou superior e npm 10 ou superior. Com asdf, o arquivo
`.tool-versions` seleciona a versão do Node.

```bash
npm ci
npm start
```

A aplicação fica em <http://localhost:4201> e encaminha `/api` para
`http://localhost:8080`. Para usar outro endereço de API:

```bash
API_PROXY_TARGET=http://localhost:8080 npm start
```

## Docker

Com o backend publicado na porta `8080`, configure o ambiente e inicie a versão
de desenvolvimento:

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

As credenciais são criadas pelo backend usando as variáveis `AUTH_SEED_ADMIN_*`
e `AUTH_SEED_TECHNICIAN_*`.
