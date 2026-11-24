# Frontend Auth2

Portal de administração do Auth2. O projeto usa Angular 19 com componentes
standalone, Angular Material, Tailwind CSS e a identidade visual dos portais Lactec.

## Docker Compose

O Compose lê `backend-portal-web/.env` ao carregar a stack. Se esse arquivo ainda
não existir, copie `backend-portal-web/.env.example` para `backend-portal-web/.env`.
Na raiz do repositório, inicie o portal administrativo e suas dependências:

```bash
docker compose -f compose.auth.yaml up -d --build frontend-auth2
```

Abra http://localhost:4202 e entre como `lactec@lactec.com` com a senha inicial
do ambiente de demonstração. Confira a [conta de demonstração](../backend-auth2/README.md#usuários-e-acesso).

## Desenvolvimento

Use Node 24 e npm 10 ou superior. A entrada exige uma conta administrativa existente
e um servidor Auth2 em execução. Veja a [execução isolada do Auth2](../backend-auth2/README.md#execução-isolada).

```bash
cd frontend-auth2
npm ci
npm start
```

Abra http://localhost:4202. O proxy de desenvolvimento encaminha `/api/**` para
`http://localhost:8081`. Para usar o Auth2 já hospedado, informe o endereço HTTPS
da API, incluindo o prefixo do serviço se houver, até o ponto imediatamente anterior
a `/api`:

```bash
API_PROXY_TARGET=https://auth.exemplo.com npm start
```

A tela de entrada usa `POST /api/admin/auth/login`. O token administrativo fica em
memória até expirar, sair ou recarregar a página. Sair encerra a sessão no navegador.
O primeiro administrador é criado pelo [procedimento de instalação do Auth2](../backend-auth2/docs/registrar-aplicacao.md).

`npm run build` gera o site estático em `dist/frontend-auth2/browser`.

## Plano

1. **Base implementada.** Separar telas em `pages`, layouts em `layouts`, contratos em
   `model`, chamadas HTTP em `services` e guardas e interceptadores em `core`.
   A rota `/login` leva à área protegida `/admin` após autenticação.
2. **Empresas e aplicações.** Criar listagens e formulários para `/api/companies` e
   `/api/services`. Oferecer emissão e revogação de tokens de aplicação e exibir o valor
   do token somente na resposta de emissão.
3. **Clientes e administradores.** Adicionar manutenção de `/api/users` e `/api/admins`,
   com vínculo a empresas e à aplicação no cadastro de clientes.
4. **Validação e publicação.** Conferir expiração, bloqueio de login e conflitos de edição
   contra o servidor. Usar o [contrato OpenAPI](../backend-auth2/docs/openapi.json) como referência.
   Servir a compilação por HTTPS, encaminhar `/api/**` ao Auth2 na mesma origem e
   devolver `index.html` para as rotas do Angular.
