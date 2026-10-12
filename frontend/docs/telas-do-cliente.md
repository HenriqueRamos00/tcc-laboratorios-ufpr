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

Acesse `http://localhost:4300`. O login de desenvolvimento é `lactec@lactec` /
`lactec` (`src/app/services/dev-login.ts`, que já existia).

## A linha de etapas

`src/app/model/analysis-pipeline.ts` é a fonte única do fluxo. São **15 etapas
em uma linha só**, atravessando três sistemas:

```
Qualificação → Em análise pela área → Elaborando proposta → Em negociação →
Aprovado pelo cliente → Aguardando entrega da amostra → Recebido →
Protocolado → Em execução → Incompleto → Completo →
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
- **Orçamentos** falam com a API de verdade. Quando ela não responde, o serviço
  cai na massa de `orcamentos.fixture.ts` para que o portal possa ser
  apresentado sem o backend no ar. A chave é
  `CAI_PARA_DEMONSTRACAO_SEM_API` em `quotes.service.ts`: mude para `false` e os
  erros voltam a subir, exibindo "Não foi possível carregar os orçamentos no
  momento".
- **Aceite e recusa** ficam registrados em memória durante a sessão
  (`QuoteDecisionService`), com o formato do POST que a aplicação Java vai expor.
- **PDF da proposta** vem do Salesforce e ainda não está integrado; a tela mostra
  o aviso em vez de um visualizador vazio.

## O que precisa de conferência técnica

**As zonas dos triângulos de Duval 4 e 5** foram desenhadas conforme o layout das
telas, não a partir das fronteiras publicadas. As do triângulo 1 seguem as regras
conhecidas (PD, T1, T2, T3, D1, D2 e DT em função de %CH₄, %C₂H₄ e %C₂H₂). Toda a
geometria está isolada em `src/app/services/fixtures/indicadores.fixture.ts`
justamente para que o laboratório valide antes de ir para produção.

Os valores das séries dos gráficos reproduzem o traçado das telas; os resultados
das tabelas são os números que aparecem nelas.

## Pendências conhecidas

- Sem testes automatizados para as telas novas.
- O item "Home" existe nas telas e não existe na navegação (o shell nunca teve
  essa rota).
- Os botões de download não baixam nada: falta o endpoint que serve o arquivo.
- A tela HU004 mostra "Ensaios" na navegação em vez de "Relatórios"; as demais
  mostram "Relatórios". Ficou "Relatórios", que é o nome usado em oito das nove
  telas.
