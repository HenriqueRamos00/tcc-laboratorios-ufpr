import type { RequestStatus } from '@/app/model/analysis-pipeline';

import { quoteDetailTour, type QuotePendingAction } from './quote-detail.tour';

function alvos(stage: RequestStatus | null, pendingAction: QuotePendingAction): string[] {
  return quoteDetailTour({ stage, pendingAction }).steps.map((step) => step.target);
}

function texto(stage: RequestStatus | null, pendingAction: QuotePendingAction, alvo: string): string {
  const passo = quoteDetailTour({ stage, pendingAction }).steps.find((step) => step.target === alvo);
  if (!passo) throw new Error(`Passo ausente: ${alvo}`);
  return passo.text;
}

describe('quoteDetailTour', () => {
  it('em negociação explica a decisão pendente e as duas formas de aceitar', () => {
    expect(alvos('negociacao', 'decide')).toEqual([
      'orcamento-resumo',
      'orcamento-proposta',
      'orcamento-decisao',
      'orcamento-baixar',
      'orcamento-recusar',
      'orcamento-aceitar',
    ]);
    expect(texto('negociacao', 'decide', 'orcamento-decisao')).toContain(
      'Este orçamento ainda não foi aprovado por você',
    );
    expect(texto('negociacao', 'decide', 'orcamento-decisao')).toContain('ordem de compra');
    expect(texto('negociacao', 'decide', 'orcamento-decisao')).toContain('termo de aceite');
  });

  it('aprovado troca os passos de decisão pelos de envio da amostra', () => {
    const passos = alvos('aprovado-pelo-cliente', 'confirm-shipment');

    expect(passos).toContain('orcamento-envio');
    expect(passos).toContain('orcamento-confirmar-envio');
    expect(passos).not.toContain('orcamento-aceitar');
    expect(passos).not.toContain('orcamento-recusar');
    expect(texto('aprovado-pelo-cliente', 'confirm-shipment', 'orcamento-envio')).toContain(
      'já foi aprovado por você',
    );
  });

  it('nas demais situações não há passo de decisão, só o de acompanhamento', () => {
    const semAcao: RequestStatus[] = [
      'qualificacao',
      'aguardando-amostra',
      'em-execucao',
      'relatorio-publicado',
      'cancelado',
      'recusado',
    ];

    for (const stage of semAcao) {
      expect(alvos(stage, 'follow'))
        .withContext(stage)
        .toEqual(['orcamento-resumo', 'orcamento-proposta', 'orcamento-acompanhamento']);
    }
  });

  it('o texto de acompanhamento muda com a situação', () => {
    const acompanhamento = (stage: RequestStatus | null) =>
      texto(stage, 'follow', 'orcamento-acompanhamento');

    expect(acompanhamento('qualificacao')).toContain('ainda não foi escrita');
    expect(acompanhamento('aguardando-amostra')).toContain('aguarda a chegada da amostra');
    expect(acompanhamento('em-execucao')).toContain('amostra está com o laboratório');
    expect(acompanhamento('relatorio-publicado')).toContain('já foi publicado');
    expect(acompanhamento('cancelado')).toContain('recusado ou cancelado');
    expect(acompanhamento('recusado')).toContain('encerrada pelo Lactec');
    expect(acompanhamento(null)).toContain('Não há ação pendente');
  });

  it('sem proposta escrita o passo da proposta diz que ela está em elaboração', () => {
    expect(texto('elaborando-proposta', 'follow', 'orcamento-proposta')).toContain(
      'ainda está sendo elaborada',
    );
    expect(texto('negociacao', 'decide', 'orcamento-proposta')).toContain('Leia a proposta completa');
  });
});
