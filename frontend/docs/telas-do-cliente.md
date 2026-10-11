# Portal do Cliente Lactec: protótipos e estado atual

Este documento registra as referências visuais das HUs do cliente e o
comportamento das telas disponíveis.

## Estado atual

As HUs 1 e 2 orientam o login e a consulta de orçamentos:

- HU1 restaura o login de demonstração da branch `angelo/fet/autolab`:
  `lactec@lactec` / `lactec`. Ele cria uma sessão local por 24 horas somente
  em desenvolvimento. A API C# ainda não implementa autenticação real.
- Outras credenciais são enviadas a `POST /api/auth/login` na base configurada.
  A sessão mantém `accessToken`, `tokenType`, `expiresIn` e `user`, com validação
  do perfil. O portal encerra a sessão após 401; um 403 mantém a sessão ativa.
- HU2 consulta `GET /api/quotes` na API C# e mantém o contrato de array. A tela
  mostra carregamento, erro com nova tentativa para resposta incompatível e
  estado vazio. Quando a API falha, o serviço usa fixtures de demonstração.
- O diálogo de acompanhamento usa a etapa normalizada do payload da API;
  etapas desconhecidas informam indisponibilidade. Protocolo e contato do
  laboratório permanecem pendentes no contrato.
- O detalhe permite baixar o PDF de `GET /api/quotes/{id}/document`.
- Equipamentos, histórico e indicadores usam `/api/equipments` na API C#.
  Relatórios continuam usando fixtures quando `window.__CLIENT_DEMO_DATA__ = true`
  em `public/runtime-config.js`, com aviso de dados de demonstração.
- Aceite e recusa não gravam decisões em memória. As rotas e ações informam que
  a integração ainda está pendente.
- A base de autenticação pode ser configurada em `public/runtime-config.js`;
  as consultas usam o proxy `/api` para a API C#.

Aceite, recusa e relatórios ainda têm integração pendente. O login local e as
fixtures permitem demonstrar as telas, mas não comprovam autenticação real nem
persistência de decisões.

## Execução local

```bash
PORTAL_WEB_PORT=4300 docker compose up -d --build web
```

Acesse `http://localhost:4300` com `lactec@lactec` / `lactec`.
O proxy encaminha `/api` à API C# publicada em `http://localhost:5125`.

## Rotas e estado

| História | Rota | Estado atual |
|---|---|---|
| HU1 Login | `/login` | Login local de demonstração; autenticação real pendente no C# |
| HU2 Orçamentos | `/cliente/orcamentos` | Array de `/api/quotes`; fallback de demonstração em falhas da API |
| HU3 Acompanhamento | ação na listagem | Diálogo por etapa conhecida do payload |
| HU4 Relatórios e resultados | `/cliente/relatorios` | Fixtures na demonstração; integração pendente |
| HU5 Equipamentos | `/cliente/equipamentos` | Listagem da API C# |
| HU6 Detalhe do equipamento | `/cliente/equipamentos/:id` | Detalhe e histórico da API C#; PDF depende de arquivo publicado |
| HU7 Indicadores | `/cliente/equipamentos/:id/indicadores` e `/cliente/equipamentos/:id/indicadores/:reportId` | Visão geral e análise do histórico da API C# |
| HU8 Detalhe do orçamento | `/cliente/orcamentos/:id` | Campos e PDF da API; decisões pendentes |
| HU9 Aceite | `/cliente/orcamentos/:id/aceite` | Indisponível até existir endpoint de aceite |
| HU10 Recusa | ação no detalhe do orçamento | Indisponível até existir endpoint de recusa |

O menu padrão mostra Orçamentos e Equipamentos. A opção de demonstração
acrescenta Relatórios com fixtures.

## Referências dos protótipos

O protótipo da HU2 mostra busca, filtros, empresa, proposta, data, contato,
status e ações por orçamento. A listagem preserva os campos que a API retornar,
inclui a descrição do serviço na busca e mantém o status original recebido. A
classificação dos filtros reconhece os rótulos conhecidos da API: pendente de
aceite em Pendentes, em andamento em Em Andamento e finalizado em Finalizados.
Status desconhecidos permanecem visíveis em Todos e informam indisponibilidade
no acompanhamento. Etapas conhecidas abrem o recorte correspondente da linha.

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
