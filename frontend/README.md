# Portal do Cliente Lactec

Aplicação Angular do Portal do Cliente. A execução local usa o Backend Lab Spring
para login e orçamentos. Equipamentos, indicadores e relatórios possuem dados
de demonstração opcionais para desenvolvimento das telas.

## Configuração da API

Antes de carregar a aplicação, ajuste `public/runtime-config.js`:

```js
window.__CLIENT_AUTH_API_URL__ = '/api';
```

A configuração define somente a base usada pelo login. As demais operações do
cliente sempre usam `/api`, encaminhado ao Spring. O contrato de autenticação
Lactec ainda será definido; até lá, o padrão usa o Spring local.

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

Abra `http://localhost:4200`. O usuário informa as credenciais que existem na
API. O portal usa o perfil e as empresas recebidos em `POST /api/auth/login`.
Orçamentos são lidos de `GET /api/client/quotes`, que responde com um array.

Para executar a imagem de produção pelo Compose isolado, use
`docker compose --profile prod up -d --build web-prod`; ela fica disponível em
`http://localhost:8082` por padrão. A porta pode ser alterada com
`PORTAL_WEB_PROD_PORT`.

## Dados de demonstração

Para demonstrar equipamentos, indicadores e relatórios, ajuste
`public/runtime-config.js` e recarregue o portal:

```js
window.__CLIENT_DEMO_DATA__ = true;
```

O padrão é `false`. Quando ativada, a opção habilita esses itens no menu e
mostra um aviso de dados de demonstração. O login continua exigindo uma conta
válida na API e a consulta de orçamentos continua usando o Spring, inclusive
quando ele retorna erro. As fixtures de orçamentos permanecem disponíveis
para testes e desenvolvimento, sem fallback automático. Aceite e recusa
continuam indisponíveis.

## Verificação

```bash
npm test -- --watch=false
npm run build
```

Consulte [`docs/telas-do-cliente.md`](docs/telas-do-cliente.md) para o estado das
HUs, rotas pendentes e adaptações em relação aos protótipos.
