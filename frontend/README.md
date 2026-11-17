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
selecionada pelo C# (`backend-lab` ou `lactec`, que usa Backend Auth2).

No modo `lactec`, o C# emite JWT de 15 minutos. A opção **Me mantenha conectado**
fica marcada inicialmente: quando marcada, a sessão usa `localStorage`; desmarcada,
usa `sessionStorage`, restrito à sessão da aba. Essa escolha também se aplica ao
modo `backend-lab` e não altera a validade do token. Token, nome, identidade e papel
ficam no armazenamento escolhido; logout limpa os dois. Angular envia Bearer
somente a `/api/**` e `/auth/me` da API e restaura o perfil com `/auth/me` após
recarregar. Nome e papel vêm da resposta de login; CNPJs ficam em
`currentIdentity().authorizations.companyCnpjs`. Expiração e 401 de operações
protegidas encerram a sessão; 403 é exibido sem logout. O login mostra a espera
indicada por `Retry-After` em 429, sem repetir a chamada automaticamente.

O termo de aceite (`/cliente/quotes/:id/aceite`) guarda um rascunho do que foi
preenchido no mesmo armazenamento da sessão (`localStorage` com **Me mantenha
conectado**, `sessionStorage` sem), na chave
`lactec.acceptance-draft:<usuário>:<orçamento>`, com versão e validade de 7 dias.
O rascunho é gravado após uma pausa na digitação e ao sair da tela; ao voltar, o
portal pergunta se o cliente quer continuar ou começar em branco. Ele é apagado
quando o orçamento recebe uma decisão (aceite, ordem de compra ou recusa), quando o
cliente descarta, no logout pelo menu, quando vence e quando outro usuário entra no
mesmo navegador. Sessão encerrada por expiração mantém o rascunho, para que o
cliente retome depois de entrar de novo. Sair da rota com alteração não enviada
abre um diálogo do portal; fechar a aba só pede a confirmação do navegador se o
rascunho não pôde ser gravado.

Antes de gravar uma nova sessão, o frontend confere o contrato da resposta, a
expiração e o perfil de cliente. No modo `lactec`, confere também o serviço e a
estrutura das autorizações, aceitando uma lista de empresas vazia. Uma tentativa
recusada ou resposta inválida preserva a sessão válida anterior. A verificação de
assinatura e a autorização dos recursos continuam no backend.

Para subir o portal com Backend Auth2 e sem Backend Lab, siga a
[configuração da API C#](../backend-portal-web/README.md#configuração-e-execução-em-lactec).
Para a demonstração completa, execute `docker compose -f compose.auth.yaml up -d --build`
na raiz e abra http://localhost:4200. As contas, senhas e vínculos de empresas estão no
[README do Auth2](../backend-auth2/README.md#usuários-e-acesso).
Cada conta vê os orçamentos da própria empresa. Equipamentos, ensaios e relatórios
vêm do AutoLAB real, que exige conexão configurada em `backend-portal-web/.env` e VPN.
As regras de publicação HTTPS e armazenamento do JWT estão na
[documentação da API C#](../backend-portal-web/README.md#configuração-e-execução-em-lactec).
No modo `lactec`, os orçamentos do Salesforce são filtrados pelos CNPJs no JWT,
inclusive o detalhe e o PDF. Sem empresas vinculadas, a lista fica vazia. O Angular
propaga erros da API e não substitui os resultados por orçamentos de demonstração.
Para desenvolvimento e testes locais, use Node 24+; o alvo `dev` do Docker inclui
Chromium para `npm test -- --watch=false`.

This project was generated using [Angular CLI](https://github.com/angular/angular-cli) version 19.2.27.

## Ajuda guiada (tour)

O botão **Ajuda** do menu lateral expande, no próprio menu, dois roteiros: **Sobre o menu** e
**Sobre esta página**.
O destaque, o balão e o foco são do [driver.js](https://driverjs.com) (MIT, versão fixada no
`package.json`); a sequência dos passos é do `TourService` (`src/app/services/tour.service.ts`).

- Os tipos ficam em `src/app/model/tour.ts`.
- Os passos de cada tela ficam ao lado dela, em `*.tour.ts` (o do menu é
  `layouts/shell/shell.tour.ts`), e a tela se registra com `registerPageTour(...)` no construtor.
- Cada passo aponta para um elemento marcado com `data-tour="nome"`. Nunca use classe de estilo
  como alvo. Quando o layout muda entre celular e desktop, marque os dois elementos com o mesmo
  nome: o tour escolhe o que estiver visível.
- Alvo ausente ou escondido é pulado. Passo com `region: 'menu'` abre a gaveta no celular.
- Passo que precisa de uma janela aberta (como a de status do orçamento) declara uma `scene`, com
  `enter` e `leave`; o tour entra na cena antes do passo e sai dela ao seguir adiante ou encerrar.
- O visual do balão está em `src/styles.css`, nas regras `.driver-popover.lactec-tour`, com os
  tokens `lactec-tour-*`.

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
