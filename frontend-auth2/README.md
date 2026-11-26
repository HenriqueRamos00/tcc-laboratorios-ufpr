# Frontend Auth2

Portal de administração de acessos do serviço Auth2. O projeto usa Angular 19 com componentes
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

## Administração de acessos

| Área | Rota | Operações |
| --- | --- | --- |
| Aplicações | `/admin/aplicacoes` | Cadastrar, editar, desativar e reativar aplicações. A aba **Tokens** permite emitir, copiar o valor na emissão e revogar credenciais. |
| Empresas | `/admin/empresas` | Cadastrar e editar nome e CNPJ. Para excluir uma empresa, remova primeiro os vínculos nas contas de clientes. |
| Clientes | `/admin/clientes` | Cadastrar contas por aplicação, consultar a confirmação de e-mail e editar nome, situação e empresas representadas. |
| Administradores | `/admin/administradores` | Criar, atualizar e excluir administradores. A troca de senha encerra as sessões da conta. O serviço exige pelo menos um administrador ativo. |

Cada listagem oferece busca local. O portal usa as operações descritas no [contrato OpenAPI](../backend-auth2/docs/openapi.json).

Em produção, sirva a compilação por HTTPS, encaminhe `/api/**` ao Auth2 na mesma origem e devolva `index.html` para as rotas do Angular.
