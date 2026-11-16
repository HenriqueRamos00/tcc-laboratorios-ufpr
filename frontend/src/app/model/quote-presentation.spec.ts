import { PIPELINE_STAGES, type RequestStatus } from './analysis-pipeline';
import type { Quote } from './quote';
import {
  stageOfQuote,
  statusAfterDecision,
  toDisplayedQuote,
  withSessionDecision,
} from './quote-presentation';

function quote(stage: string, laboratoryStage?: string | null): Quote {
  return {
    id: 'quote-1',
    code: 'SET_2026_47711_V_1',
    name: 'Ensaio em cabos',
    status: 'Em Andamento',
    stage,
    laboratoryStage,
    description: 'Ensaio em cabos',
    companyName: 'Empresa Exemplo Alfa Ltda',
    externalContactName: 'Fulano de Tal',
    externalContactEmail: 'fulano@exemplo.com',
    totalPrice: 1000,
    createdDate: '2026-05-05',
  };
}

describe('stageOfQuote, a etapa que a tela mostra', () => {
  it('sem etapa do laboratório vale a etapa comercial', () => {
    expect(stageOfQuote(quote('Aprovado pelo cliente'))).toBe('aprovado-pelo-cliente');
    expect(stageOfQuote(quote('Aprovado pelo cliente', null))).toBe('aprovado-pelo-cliente');
    expect(stageOfQuote(quote('Aprovado pelo cliente', ''))).toBe('aprovado-pelo-cliente');
  });

  it('a etapa do laboratório vale quando está à frente da comercial', () => {
    const laboratory: readonly RequestStatus[] = [
      'recebido',
      'protocolado',
      'em-execucao',
      'completo',
      'validado',
      'finalizado',
      'elaborando-relatorio',
      'relatorio-publicado',
    ];

    for (const stage of laboratory) {
      expect(stageOfQuote(quote('Aprovado pelo cliente', stage))).toBe(stage);
      expect(stageOfQuote(quote('Aguardando entrega da amostra', stage))).toBe(stage);
    }
  });

  it('toda chave da linha de etapas é reconhecida como etapa do laboratório', () => {
    for (const stage of PIPELINE_STAGES.slice(1)) {
      expect(stageOfQuote(quote('Qualificação', stage.key))).toBe(stage.key);
    }
  });

  it('a etapa comercial vale quando o laboratório está atrás ou na mesma etapa', () => {
    expect(stageOfQuote(quote('Fechado ganho', 'recebido'))).toBe('recebido');
    expect(stageOfQuote(quote('Relatório publicado', 'em-execucao'))).toBe('relatorio-publicado');
    expect(stageOfQuote(quote('Em execução', 'protocolado'))).toBe('em-execucao');
  });

  it('orçamento cancelado ou recusado continua fechado, com o laboratório adiantado ou não', () => {
    expect(stageOfQuote(quote('Fechado perdido', 'relatorio-publicado'))).toBe('cancelado');
    expect(stageOfQuote(quote('Cancelado', 'em-execucao'))).toBe('cancelado');
    expect(stageOfQuote(quote('Fechado recusado Lactec', 'validado'))).toBe('recusado');
  });

  it('valor desconhecido de etapa do laboratório é ignorado', () => {
    const unknown = [
      'report-published',
      'in-progress',
      'Em execução',
      'EM-EXECUCAO',
      'cancelado',
      'recusado',
      'qualquer-coisa',
    ];

    for (const value of unknown) {
      expect(stageOfQuote(quote('Aprovado pelo cliente', value))).toBe('aprovado-pelo-cliente');
    }
  });
});

describe('toDisplayedQuote, com a etapa do laboratório', () => {
  it('a situação exibida é a do laboratório quando ele está à frente', () => {
    const displayed = toDisplayedQuote(quote('Aprovado pelo cliente', 'em-execucao'));

    expect(displayed.status).toBe('em-execucao');
    expect(displayed.hasProposal).toBeTrue();
  });

  it('o orçamento fechado continua fechado na listagem', () => {
    expect(toDisplayedQuote(quote('Fechado perdido', 'finalizado')).status).toBe('cancelado');
  });
});

describe('statusAfterDecision, a decisão tomada nesta sessão', () => {
  it('sem decisão vale a etapa carregada', () => {
    expect(statusAfterDecision('negociacao', undefined)).toBe('negociacao');
  });

  it('a decisão vale enquanto a etapa carregada não passou dela', () => {
    expect(statusAfterDecision('negociacao', 'aprovado-pelo-cliente')).toBe(
      'aprovado-pelo-cliente',
    );
    expect(statusAfterDecision('aprovado-pelo-cliente', 'aguardando-amostra')).toBe(
      'aguardando-amostra',
    );
  });

  it('a etapa carregada vale quando o laboratório já passou da decisão', () => {
    expect(statusAfterDecision('em-execucao', 'aguardando-amostra')).toBe('em-execucao');
  });

  it('a recusa desta sessão fecha o orçamento em qualquer etapa', () => {
    expect(statusAfterDecision('negociacao', 'recusado')).toBe('recusado');
  });

  it('a listagem aplica a mesma regra', () => {
    const ahead = toDisplayedQuote(quote('Aprovado pelo cliente', 'protocolado'));
    const behind = toDisplayedQuote(quote('Negociação'));

    expect(withSessionDecision(ahead, 'aguardando-amostra')).toBe(ahead);
    expect(withSessionDecision(behind, 'aprovado-pelo-cliente').status).toBe(
      'aprovado-pelo-cliente',
    );
  });
});
