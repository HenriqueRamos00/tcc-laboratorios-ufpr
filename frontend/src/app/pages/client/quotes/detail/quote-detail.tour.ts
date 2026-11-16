import { isClosed, type RequestStatus } from '@/app/model/analysis-pipeline';
import { hasProposal } from '@/app/model/quote-presentation';
import type { TourDefinition, TourStep } from '@/app/model/tour';

export type QuotePendingAction = 'decide' | 'confirm-shipment' | 'follow';

export interface QuoteDetailTourContext {
  readonly stage: RequestStatus | null;
  readonly pendingAction: QuotePendingAction;
}

const FOLLOW_TITLE = 'Situação deste orçamento';

function proposalStep(stage: RequestStatus | null): TourStep {
  const written = stage !== null && hasProposal(stage);
  return {
    id: 'proposta',
    target: 'orcamento-proposta',
    side: 'top',
    title: 'Proposta',
    text: written
      ? 'Leia a proposta completa aqui mesmo. A barra do visualizador permite ampliar, procurar no texto e abrir em tela cheia.'
      : 'A proposta ainda está sendo elaborada pelo laboratório. Quando ficar pronta, o documento aparece neste espaço para leitura e download.',
  };
}

function decisionSteps(): TourStep[] {
  return [
    {
      id: 'decisao',
      target: 'orcamento-decisao',
      side: 'top',
      title: 'Aguardando a sua decisão',
      text: 'Este orçamento ainda não foi aprovado por você. É possível aceitar incluindo uma ordem de compra ou preenchendo nosso termo de aceite.',
    },
    {
      id: 'baixar',
      target: 'orcamento-baixar',
      side: 'top',
      title: 'Baixar PDF',
      text: 'Baixa a proposta em PDF para guardar ou encaminhar a quem precisa aprovar com você.',
    },
    {
      id: 'recusar',
      target: 'orcamento-recusar',
      side: 'top',
      title: 'Recusar Orçamento',
      text: 'Se a proposta não atende, recuse por aqui. O portal pede uma confirmação antes de registrar a recusa.',
    },
    {
      id: 'aceitar',
      target: 'orcamento-aceitar',
      side: 'top',
      title: 'Aceitar Orçamento',
      text: 'Ao aceitar, você escolhe como formalizar: anexando uma ordem de compra ou preenchendo o termo de aceite do Lactec.',
    },
  ];
}

function shipmentSteps(): TourStep[] {
  return [
    {
      id: 'envio',
      target: 'orcamento-envio',
      side: 'top',
      title: 'Orçamento aprovado',
      text: 'Este orçamento já foi aprovado por você. O próximo passo é enviar a amostra: aqui estão o endereço e como identificar a caixa.',
    },
    {
      id: 'confirmar-envio',
      target: 'orcamento-confirmar-envio',
      side: 'top',
      title: 'Confirmar envio da amostra',
      text: 'Depois de despachar a amostra, avise por este botão. O laboratório passa a aguardar o recebimento.',
    },
  ];
}

function followText(stage: RequestStatus | null): string {
  if (stage === null) {
    return 'Não há ação pendente para você neste orçamento. Acompanhe a situação pela listagem de orçamentos.';
  }
  if (stage === 'recusado') {
    return 'Esta solicitação foi encerrada pelo Lactec e não avança mais. Não há ação pendente para você.';
  }
  if (isClosed(stage)) {
    return 'Este orçamento foi recusado ou cancelado e não avança mais. Se quiser retomar o serviço, fale com o laboratório.';
  }
  if (!hasProposal(stage)) {
    return 'A proposta ainda não foi escrita, então não há o que aprovar. Assim que ela ficar pronta, as opções de aceitar e recusar aparecem aqui.';
  }
  if (stage === 'aguardando-amostra') {
    return 'Você já aprovou este orçamento e confirmou o envio. O laboratório aguarda a chegada da amostra e não há ação pendente para você.';
  }
  if (stage === 'relatorio-publicado') {
    return 'O relatório deste orçamento já foi publicado. Você o encontra na página de Relatórios.';
  }
  return 'Este orçamento já foi aprovado e a amostra está com o laboratório. Não há ação pendente: acompanhe as etapas pela listagem de orçamentos.';
}

function situationSteps(context: QuoteDetailTourContext): TourStep[] {
  if (context.pendingAction === 'decide') return decisionSteps();
  if (context.pendingAction === 'confirm-shipment') return shipmentSteps();
  return [
    {
      id: 'acompanhamento',
      target: 'orcamento-acompanhamento',
      side: 'top',
      title: FOLLOW_TITLE,
      text: followText(context.stage),
    },
  ];
}

export function quoteDetailTour(context: QuoteDetailTourContext): TourDefinition {
  return {
    id: 'orcamento',
    label: 'Sobre esta página',
    steps: [
      {
        id: 'resumo',
        target: 'orcamento-resumo',
        side: 'bottom',
        title: 'Resumo do orçamento',
        text: 'Aqui estão a empresa atendida e o valor total deste orçamento.',
      },
      proposalStep(context.stage),
      ...situationSteps(context),
    ],
  };
}
