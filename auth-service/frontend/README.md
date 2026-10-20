# Painel Administrativo do Auth Service

Aplicação Angular 19 para administrar usuários, empresas e chaves de integração do
auth-service. O visual, o tema do Angular Material, o Tailwind e os componentes de
diálogo são os mesmos do Portal Interno.

## Desenvolvimento

```bash
npm ci
API_PROXY_TARGET=http://localhost:9000 npm start
```

A aplicação fica em <http://localhost:4202>. O proxy encaminha `/api` para o
auth-service.

## Build

```bash
npm run build
```

A saída fica em `dist/auth-admin/browser`.

## Autenticação

O painel usa sessão HttpOnly criada por `POST /api/admin/auth/login`. O token CSRF
é obtido em `GET /api/admin/auth/csrf` e enviado no cabeçalho `X-CSRF-TOKEN`; não
há token em `localStorage`. O Angular envia esse cabeçalho automaticamente pelo
interceptor `admin-csrf.interceptor.ts`.
