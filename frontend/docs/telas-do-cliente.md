# Portal do Cliente Lactec: protótipos e estado atual

Este documento registra as referências visuais das HUs do cliente e o
comportamento das telas disponíveis.

## Estado atual

As HUs 1 e 2 orientam o login e a consulta de orçamentos:

- HU1 usa `POST /api/auth/login` com `{ email, password }` e recebe `accessToken`,
  `tokenType`, `expiresIn` e `user`. A interface usa `user.role`, `user.name` e
  `user.companies`; não usa claims antigos para identificar o papel.
- O login antigo de desenvolvimento foi removido. A sessão anterior incompatível
  é apagada. O portal envia Bearer ao Spring e encerra a sessão após 401. Um 403
  informa falta de permissão e mantém a sessão ativa.
- HU2 consulta `GET /api/client/quotes` e mantém o contrato de array. A tela
  mostra carregamento durante a consulta, erro com opção de tentar novamente
  em caso de falha e estado vazio quando não há resultados.
- O status da listagem vem do payload da API. O acompanhamento detalhado não
  está ligado a uma fonte autorizada e permanece indisponível.
- O detalhe permite baixar o PDF de `GET /api/client/quotes/{id}/document` com Bearer
  no cabeçalho. O token não é enviado na URL.
- Equipamentos, indicadores e relatórios mostram indisponibilidade no modo
  padrão. Com `window.__CLIENT_DEMO_DATA__ = true` em `public/runtime-config.js`,
  suas telas usam fixtures e exibem um aviso de dados de demonstração. Essa
  opção preserva o desenvolvimento visual enquanto a integração está pendente.
- Aceite e recusa não gravam decisões em memória. As rotas e ações informam que
  a integração ainda está pendente.
- A base de autenticação pode ser configurada em `public/runtime-config.js`; as
  demais operações continuam usando o Spring em `/api`. O contrato Lactec ainda
  está pendente.

As adaptações acima divergem temporariamente dos protótipos das HUs 3 a 10. O
motivo é que os dados e operações dessas telas não têm integração autorizada
disponível no fluxo local. O bloqueio evita exibir etapas, relatórios,
equipamentos e decisões simuladas como registros reais. A especificação técnica
registra essa cobertura parcial e os critérios externos ainda não atendidos.

## Execução local

```bash
PORTAL_WEB_PORT=4300 docker compose up -d --build web
```

Acesse `http://localhost:4300`. O proxy encaminha `/api` ao Backend Lab; as credenciais são validadas pelo
servidor.

## Rotas e estado

| História | Rota | Estado atual |
|---|---|---|
| HU1 Login | `/login` | Login real via Spring local e perfil da resposta |
| HU2 Orçamentos | `/cliente/orcamentos` | Array de `/api/client/quotes`, carregamento, erro com nova tentativa e estado vazio |
| HU3 Acompanhamento | ação na listagem | Estado pendente; status detalhado não é simulado |
| HU4 Relatórios e resultados | `/cliente/relatorios` | Fixtures na demonstração; integração pendente |
| HU5 Equipamentos | `/cliente/equipamentos` | Fixtures na demonstração; integração pendente |
| HU6 Detalhe do equipamento | `/cliente/equipamentos/:id` | Histórico e PDF de exemplo na demonstração; integração pendente |
| HU7 Indicadores | `/cliente/equipamentos/:id/indicadores` e `/cliente/equipamentos/:id/indicadores/:relatorioId` | Visão geral e análise do histórico na demonstração; integração pendente |
| HU8 Detalhe do orçamento | `/cliente/orcamentos/:id` | Campos e PDF da API; decisões pendentes |
| HU9 Aceite | `/cliente/orcamentos/:id/aceite` | Indisponível até existir endpoint de aceite |
| HU10 Recusa | ação no detalhe do orçamento | Indisponível até existir endpoint de recusa |

O menu padrão mostra apenas Orçamentos. A opção de demonstração acrescenta
Equipamentos e Relatórios; as rotas correspondentes carregam suas telas com
fixtures. O login e os orçamentos mantêm o fluxo real em ambos os modos.

## Referências dos protótipos

O protótipo da HU2 mostra busca, filtros, empresa, proposta, data, contato,
status e ações por orçamento. A listagem preserva os campos que a API retornar,
inclui a descrição do serviço na busca e mantém o status original recebido. A
classificação dos filtros reconhece os rótulos conhecidos da API: pendente de
aceite em Pendentes, em andamento em Em Andamento e finalizado em Finalizados.
Status desconhecidos permanecem visíveis em Todos. A ação de acompanhamento
exibe o estado pendente porque a linha de etapas ainda
não vem de uma fonte autorizada.

Outras divergências históricas das telas continuam registradas para quando as
integrações forem retomadas:

1. O protótipo HU3_03 inclui “Protocolado” e “Em execução”, etapas ausentes da
   lista escrita do critério 3 da HU3.
2. A tela HU10 usa a confirmação “Deseja realmente recusar o orçamento” com os
   botões “Rejeitar” e “Cancelar”; o critério textual descreve outra mensagem e
   os botões “Confirmar” e “Cancelar”.
3. O protótipo HU8_03 mostra cobrança e aprovação, enquanto o critério da HU9 e o
   Anexo II também descrevem endereço de envio do relatório.

As fixtures de indicadores preservam as coletas, tabelas e gráficos usados no
desenvolvimento das telas. As de equipamentos preservam o histórico e a
seleção da análise na URL. Esses exemplos não comprovam integração nem
validação laboratorial dos resultados.
