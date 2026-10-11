# Portal do Cliente Lactec

Aplicação Angular do Portal do Cliente, conectada ao `backend-portal-web` C#.
Orçamentos, equipamentos e indicadores são consultados na API. Relatórios
possuem dados de demonstração opcionais para desenvolvimento das telas.

## Configuração da API

Antes de carregar a aplicação, ajuste `public/runtime-config.js`:

```js
window.__CLIENT_AUTH_API_URL__ = '/api';
```

A configuração define a base usada por um futuro login real. A API C# ainda
não implementa esse endpoint. No desenvolvimento, use o login de demonstração
da branch `angelo/fet/autolab`: e-mail `lactec@lactec`, senha `lactec`.
Esse login cria uma sessão local por 24 horas e é desativado no build de produção.
Outras credenciais são encaminhadas para `POST /api/auth/login`.

As consultas usam `/api` na origem do frontend. O proxy padrão aponta para
`http://localhost:5125`; no Compose da raiz, aponta para `http://portal-api:8080`.
O Compose isolado e o Nginx usam `http://host.docker.internal:5125` por padrão.

## Executar

Com Docker Compose:

```bash
docker compose up -d --build web
```

Ou com Node.js 24 ou superior:

```bash
npm install
npm start
```

Abra `http://localhost:4200` e entre com `lactec@lactec` / `lactec`.
Orçamentos são lidos de `GET /api/quotes`, que responde com um array; o PDF usa
`GET /api/quotes/{id}/document`. Equipamentos e indicadores usam `/api/equipments`.
Para iniciar também a API C# a partir da raiz do repositório, use
`docker compose up -d --build frontend`.

Para executar a imagem de produção pelo Compose isolado, use
`docker compose --profile prod up -d --build web-prod`; ela fica disponível em
`http://localhost:8082` por padrão. A porta pode ser alterada com
`PORTAL_WEB_PROD_PORT`.

## Dados de demonstração

Para demonstrar relatórios, ajuste
`public/runtime-config.js` e recarregue o portal:

```js
window.__CLIENT_DEMO_DATA__ = true;
```

O padrão é `false`. Quando ativada, a opção habilita Relatórios no menu e
mostra um aviso de dados de demonstração. Equipamentos e indicadores usam a
API C# em ambos os modos. A consulta de orçamentos usa as fixtures locais se
a API falhar, conforme a configuração em `quotes.service.ts`. Aceite e recusa
continuam indisponíveis.

## Verificação

```bash
npm test -- --watch=false
npm run build
```

Consulte [`docs/telas-do-cliente.md`](docs/telas-do-cliente.md) para o estado das
HUs, rotas pendentes e adaptações em relação aos protótipos.
