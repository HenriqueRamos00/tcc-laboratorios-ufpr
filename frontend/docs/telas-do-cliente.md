# Portal do Cliente Lactec: telas implementadas

Registro do que foi construído a partir das telas aprovadas (`HU003` a `HU010`) e
do documento de histórias de usuário. Serve para a revisão: cada seção diz o que
foi feito, qual decisão foi tomada onde a documentação e a tela divergiam, e o
que ficou pendente.

## Como rodar

```bash
# a porta do host é parametrizável; o padrão continua 4200
PORTAL_WEB_PORT=4300 docker compose up -d --build web
```

Acesse `http://localhost:4300`. Autentique um cliente cadastrado na fonte da API C#.
O login de desenvolvimento foi removido; consulte a [integração de autenticação](../../backend-portal-web/README.md#autenticação-do-portal-do-cliente).

## A linha de etapas

`src/app/model/analysis-pipeline.ts` é a fonte única do fluxo. São **14 etapas
em uma linha só**, atravessando três sistemas:

```
Qualificação → Em análise pela área → Elaborando proposta → Em negociação →
Aprovado pelo cliente → Aguardando entrega da amostra → Recebido →
Protocolado → Em execução → Completo →
Validado → Finalizado → Elaborando relatório → Relatório publicado
```

O cliente vê essa linha em **três recortes**, que são exatamente os três modais
das telas. Eles se sobrepõem nas bordas de propósito, porque a última etapa de
um é a primeira do próximo: é assim que a tela mostra a passagem de bastão:

| Recorte | Etapas | Tela |
|---|---|---|
| Status da Proposta | Qualificação → Recebido | HU003_01 e HU003_02 |
| Etapas da Análise Técnica das amostras | Recebido → Completo | HU003_03 |
| Procedimento dos Laboratórios | Completo → Relatório publicado | HU003_04 |

**Cancelamento encerra a solicitação em qualquer ponto.** A situação vira
terminal, a linha para de avançar, o registro passa a aparecer na aba
*Cancelados* e a etapa alcançada continua guardada, para o cliente ver até onde
o processo chegou. Sair de *Em negociação* é a única transição que o cliente
controla: aceitar leva a *Aprovado pelo cliente*, recusar encerra.

### Divergências entre o documento e as telas

O documento foi seguido, com três exceções onde a tela é mais recente e foi
tratada como a fonte correta:

1. **"Protocolado" e "Em execução"** aparecem no modal de análise técnica
   (HU003_03) e **não constam** da lista escrita do critério 3 da HU-03. Foram
   incluídos na linha.
2. **O texto de confirmação da recusa.** O documento pede "Tem certeza que
   deseja recusar este orçamento? Esta ação não poderá ser desfeita." com botões
   "Confirmar"/"Cancelar"; a tela HU010 usa `Deseja realmente recusar o orçamento
   "<nome>"?` com "Rejeitar"/"Cancelar". Ficou como está na tela.
3. **Endereço para envio do relatório.** A tela HU008_03 mostra só duas seções
   (cobrança e aprovação), mas o critério 5 da HU-09 e a descrição do Anexo II
   exigem a seção de envio do relatório com a opção "Mesmo endereço da cobrança".
   Ela foi incluída como seção 2, e a aprovação virou a 3. **Ponto a confirmar
   na revisão.**

## Telas

| História | Rota | Estado |
|---|---|---|
| HU-02/03 Listagem e status | `/cliente/orcamentos` | Já existia; ligada à linha de etapas e aos três recortes |
| HU-04 Conclusão da análise | `/cliente/relatorios` | Novo |
| HU-05 Listagem de equipamentos | `/cliente/equipamentos` | Novo |
| HU-06 Detalhes do equipamento | `/cliente/equipamentos/:id` | Novo |
| HU-07 Indicadores de saúde | `/cliente/equipamentos/:id/indicadores` | Novo |
| HU-08 Detalhes do orçamento | `/cliente/orcamentos/:id` | Novo |
| HU-09 Termo de aceite | `/cliente/orcamentos/:id/aceite` | Novo |
| HU-10 Recusa | diálogo em `/cliente/orcamentos/:id` | Novo |

## Situação dos equipamentos pelos laudos assinados

A listagem (`/cliente/equipamentos`) tem a coluna **Situação** e o filtro de mesmo nome.

- **Classificação pela cor.** A situação vem da cor com que o laudo assinado pinta a célula
  RESULTADO: verde é Normal, amarelo é Alerta, vermelho é Não conforme. O texto do rótulo
  não decide nada: um rótulo "NORMAL" numa célula amarela é Alerta. Cor que não é nenhuma
  das três deixa o laudo sem classificação, e o rótulo continua aparecendo, sem cor. A
  situação do equipamento é a pior entre cromatografia e físico-química, cada uma tirada
  do laudo mais recente que tem a seção, entre todos os laudos de todos os protocolos do
  equipamento. Laudo sem as tabelas de RESULTADO (PCB, por exemplo) não decide nada:
  equipamento que só tem laudos assim fica "Sem laudo", e "Sem classificação" fica para a
  seção lida cuja cor não foi reconhecida. A tela de
  indicadores usa a mesma regra no selo de cada laudo e na faixa de conformidade.
- **Pílulas.** A situação sai numa pílula preenchida pela cor do estado, do componente base
  `shared/components/status-pill`, que tem duas versões com as mesmas classes de forma
  (`STATUS_PILL_CLASS`: `h-6`, `w-40`, `rounded-full`, `px-2`, `text-xs font-semibold`):
  - `app-status-pill`, o rótulo, um `span` sem ação. Os tons usam os pares da tarja de
    conformidade do `@theme`, que já foram medidos para texto por cima: Normal é
    `bg-lactec-tarja-normal` com texto branco (6,47:1), Alerta é `bg-lactec-tarja-alerta`
    com `text-lactec-on-tarja-alerta` (7,60:1) e Não conforme é
    `bg-lactec-tarja-nao-conforme` com texto branco (8,38:1). "Sem laudo" e "Sem
    classificação" usam o tom neutro, `bg-lactec-muted` com texto branco (4,91:1). Os tons
    de feedback (`success`, `warning`, `danger`) não entram porque branco sobre `danger`
    dá 3,96:1 e sobre `warning` dá 2,15:1.
  - `app-status-pill-button`, o botão, um `button` com a mesma pílula no tom neutro e o
    ícone `sync` do Material Icons antes do texto "Atualizar". Sem sublinhado, com foco
    visível. Enquanto atualiza, o ícone gira (`animate-spin`, parado para quem pede menos
    movimento), o texto vira "Atualizando" e o botão fica `disabled` e `aria-busy`.

  `app-equipment-status` (`shared/components/equipment-status`) decide qual das duas entra
  e **reserva o espaço**: o próprio elemento tem `h-6 w-40`, igual à pílula, em todas as
  variantes. Trocar rótulo, botão e "Atualizando" não muda a largura da coluna nem a altura
  da linha. Por isso a falha não abre uma segunda linha de texto: o botão passa a dizer
  "Tentar de novo" e o aviso "Não foi possível atualizar a situação de ..." vai para o
  leitor de tela (`role="alert"`, `sr-only`). A tabela de ensaios da tela de indicadores usa
  o mesmo `app-status-pill` na coluna de classificação.
- **Sincronização automática.** Ao exibir uma página da lista, as linhas visíveis que a API
  devolveu com `status: null` são pedidas sozinhas, num único
  `POST /api/equipments/status/refresh` com os identificadores da página (em fatias de 100,
  o teto da API). As linhas ficam em "Atualizando" e a API responde 202 na hora. Quando o
  aviso `carga.concluida` chega em `GET /api/events`, a página é recarregada em silêncio e
  cada linha troca para a pílula. A tela guarda as chaves que já pediu: mudar de página, de
  busca ou de filtro e voltar não repete o pedido da mesma chave. Resposta com
  `accepted: 0` (tudo já estava na projeção) recarrega sem esperar aviso; aviso que não
  chega em 2 minutos também. Linha que continua sem situação depois disso fica com o botão
  Atualizar. Linha sem o campo `status` (API sem a projeção) não entra no lote.
- **Atualizar da linha.** O clique faz `POST /api/equipments/{id}/status/refresh` e o botão
  passa a "Atualizando". Se a resposta já traz a situação, a pílula entra na hora. Se outra
  rodada da carga estava em andamento (`alreadyRunning`), a linha espera o aviso
  `carga.concluida` e então busca `GET /api/equipments/{id}/status`. Conexão que cai ou
  aviso que não chega em 2 minutos caem na mesma consulta. Ambiente sem a projeção recolhe
  o botão de todas as linhas e o do título.
- **Atualizar do título.** Na linha do título, à direita, um botão primário (o mesmo estilo
  de "Ver Detalhes", com o ícone `sync` à direita) faz `POST /api/equipments/status/sync`,
  que pede a leitura dos laudos dos demais equipamentos do cliente, em todos os CNPJs da
  sessão. A API responde 202 e trabalha em segundo plano; se já havia rodada, quem pediu
  entra como interessado. O botão mostra "Atualizando…" até o aviso `carga.concluida`, e
  então a lista é recarregada e a página volta a pedir sozinha o que continuou sem
  situação. Sem aviso em 15 minutos o botão volta e a lista é recarregada. Falha mostra
  "Não foi possível atualizar." abaixo do botão.
- **Uma conexão de avisos.** Os pedidos em andamento dividem uma conexão com
  `GET /api/events`, aberta antes do primeiro pedido para o aviso não se perder.
- **Avisos sem `EventSource`.** O `EventSource` do navegador não manda cabeçalho, e a API só
  aceita o token no `Authorization`. `services/server-events.ts` lê o fluxo pelo
  `HttpClient`, que passa pelo interceptor como as demais chamadas.
- **Filtro.** Não conforme, Alerta, Normal, Sem laudo e Sem classificação. O filtro e a
  paginação são feitos no servidor, numa consulta à projeção local (`?status=`), e só
  alcançam equipamento que já teve os laudos lidos. Os demais aparecem em "Todas" e são
  atualizados ao serem exibidos.
- **De onde vem o dado.** A API lê os laudos numa rodada diária às 06:00, só dos CNPJs
  ativados, e a primeira carga de um cliente grande leva mais de um dia. Os pedidos da
  tela (página exibida, linha e título) entram na mesma vaga dessa rodada. A ativação de um
  CNPJ e as tabelas estão em `backend-portal-web/README.md`, seção "Situação dos
  equipamentos e carga dos laudos".

## Gráficos

Não entrou biblioteca de gráficos: `app-line-chart` desenha em SVG e suporta
escala linear e logarítmica, porque os dois casos aparecem na HU-07. Os ensaios
físico-químicos compartilham ordem de grandeza; a cromatografia não (o
nitrogênio é quatro ordens acima do acetileno) e só fica legível em log.

A paleta categórica de oito matizes foi validada para superfície clara nos
critérios de faixa de luminosidade, piso de croma, separação para daltonismo e
piso de visão normal. Como a cromatografia tem nove séries, a nona não ganha uma
matiz inventada: ela repete a primeira cor com traço tracejado. Além disso cada
série tem uma **forma de marcador** repetida na legenda, e o cruzamento do mouse
mostra todas as séries de uma vez: identidade nunca depende só da cor. As
tabelas logo abaixo de cada gráfico cumprem o papel de vista alternativa.

## Onde os dados são de demonstração

Nada disso é invenção escondida: está tudo isolado em `src/app/services/fixtures/`
e comentado no lugar de uso.

- **Equipamentos, indicadores e relatórios** não têm endpoint no AutoLAB ainda.
  Os serviços (`EquipmentsService`, `HealthService`, `ReportsService`) devolvem a
  massa com a mesma assinatura que a versão HTTP vai ter; trocar é substituir o
  corpo de cada método, sem tocar nas telas.
- **Orçamentos** falam com a API C#. No modo `lactec`, a API consulta Salesforce
  com os CNPJs autorizados no JWT; o modo `backend-lab` mantém seu adapter de
  demonstração no C#. Erros de consulta são exibidos na tela, sem fallback para
  fixtures no Angular.
- **Aceite, ordem de compra, envio de amostra e recusa** são gravados pela API C#
  no banco do portal (`QuoteDecisionService` faz os POSTs). O termo de aceite
  pela metade fica como rascunho no navegador até ser enviado ou descartado.
- **PDF da proposta** vem do Salesforce e é exibido no visualizador da própria
  tela, que também abre em tela cheia.

## O que precisa de conferência técnica

**As zonas dos triângulos de Duval 4 e 5** foram desenhadas conforme o layout das
telas, não a partir das fronteiras publicadas. As do triângulo 1 seguem as regras
conhecidas (PD, T1, T2, T3, D1, D2 e DT em função de %CH₄, %C₂H₄ e %C₂H₂). Toda a
geometria está isolada em `src/app/services/fixtures/indicadores.fixture.ts`
justamente para que o laboratório valide antes de ir para produção.

**Os valores típicos por gás da IEC 60599** (`src/app/model/fault-indication.ts`)
decidem quando a tabela "Diagnóstico" avisa que as indicações dos métodos não foram
expressivas e servem apenas de apoio. Eles foram transcritos sem a faixa e sem a
classe de equipamento a que pertencem; a norma publica faixas de percentil que
variam conforme o equipamento e o histórico da frota. A tela cita a Tabela A.1 da
IEC 60599:2015, e os valores e a tabela precisam ser conferidos pelo laboratório
antes de ir para produção.

Os valores das séries dos gráficos reproduzem o traçado das telas; os resultados
das tabelas são os números que aparecem nelas.

## Pendências conhecidas

- Sem testes automatizados para as telas novas.
- O item "Home" existe nas telas e não existe na navegação (o shell nunca teve
  essa rota).
- A tela HU004 mostra "Ensaios" na navegação em vez de "Relatórios"; as demais
  mostram "Relatórios". Ficou "Relatórios", que é o nome usado em oito das nove
  telas.
