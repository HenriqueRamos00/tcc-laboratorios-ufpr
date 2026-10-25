# PortalWeb

Portal do Cliente Lactec: acompanhamento de orçamentos, status de ensaios,
relatórios e indicadores de saúde de equipamentos.

As telas do cliente, a linha de etapas do processo e o que ainda é massa de
demonstração estão documentados em [`docs/telas-do-cliente.md`](docs/telas-do-cliente.md).

## Subir com Docker

```bash
docker compose up -d --build web            # http://localhost:4200
PORTAL_WEB_PORT=4300 docker compose up -d web  # outra porta, se a 4200 estiver ocupada
```

O login usa `POST /auth/login` da API C#, configurada em `src/app/environment/env.ts`.
Não há conta ou token de desenvolvimento no frontend. Cadastre o cliente na fonte
selecionada pelo C# (`backend-lab` ou `lactec`, que usa Backend Auth).

No modo `lactec`, o C# emite JWT de 15 minutos. Angular guarda a sessão em
`localStorage` (`lactec.token`, `lactec.username`, `lactec.user`), envia Bearer
somente a `/api/**` e `/auth/me` da API e restaura o perfil com `/auth/me` após
recarregar. Nome e papel vêm da resposta de login; CNPJs ficam em
`currentIdentity().authorizations.companyCnpjs`. Expiração e 401 de operações
protegidas encerram a sessão; 403 é exibido sem logout. O login mostra a espera
indicada por `Retry-After` em 429, sem repetir a chamada automaticamente.

Para subir o portal com Backend Auth e sem Backend Lab, siga a
[configuração da API C#](../backend-portal-web/README.md#configuração-e-execução-em-lactec).
Para a demonstração completa, execute `docker compose -f compose.auth.yaml up -d --build`
na raiz e abra http://localhost:4200. Entre com `client1@lactec.com` ou
`client2@lactec.com`, usando o próprio e-mail como senha. Cada conta vê seus
orçamentos e ensaios. `lactec@lactec.com` é administrador somente do Backend Auth.
As fixtures da tela de relatórios são filtradas pelos orçamentos autorizados na API:
client1 recebe os quatro relatórios de `quote-18`; client2 recebe cinco de
`quote-15` e `quote-19`. Os PDFs de laudos continuam sendo os exemplos locais existentes.
As regras de publicação HTTPS e armazenamento do JWT também estão documentadas lá.
No modo `lactec`, os orçamentos do Salesforce são filtrados pelos CNPJs no JWT,
inclusive o detalhe e o PDF. Sem empresas vinculadas, a lista fica vazia. O Angular
propaga erros da API e não substitui os resultados por orçamentos de demonstração.
Para desenvolvimento e testes locais, use Node 24+; o alvo `dev` do Docker inclui
Chromium para `npm test -- --watch=false`.

This project was generated using [Angular CLI](https://github.com/angular/angular-cli) version 19.2.27.

## Development server

To start a local development server, run:

```bash
ng serve
```

Once the server is running, open your browser and navigate to `http://localhost:4200/`. The application will automatically reload whenever you modify any of the source files.

## Code scaffolding

Angular CLI includes powerful code scaffolding tools. To generate a new component, run:

```bash
ng generate component component-name
```

For a complete list of available schematics (such as `components`, `directives`, or `pipes`), run:

```bash
ng generate --help
```

## Building

To build the project run:

```bash
ng build
```

This will compile your project and store the build artifacts in the `dist/` directory. By default, the production build optimizes your application for performance and speed.

## Running unit tests

To execute unit tests with the [Karma](https://karma-runner.github.io) test runner, use the following command:

```bash
ng test
```

## Running end-to-end tests

For end-to-end (e2e) testing, run:

```bash
ng e2e
```

Angular CLI does not come with an end-to-end testing framework by default. You can choose one that suits your needs.

## Additional Resources

For more information on using the Angular CLI, including detailed command references, visit the [Angular CLI Overview and Command Reference](https://angular.dev/tools/cli) page.
