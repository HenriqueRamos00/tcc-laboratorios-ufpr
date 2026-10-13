import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';

import type { SituacaoDaSolicitacao } from '@/app/model/analysis-pipeline';
import { indiceDaEtapa, LINHA_DE_ETAPAS } from '@/app/model/analysis-pipeline';
import type { Quote } from '@/app/model/quote';
import { AppDialogService } from '@/app/services/app-dialog.service';
import { LoginService } from '@/app/services/login.service';
import { QuotesService } from '@/app/services/quotes.service';

import type { ProposalStatusDialogData } from './proposal-status-dialog/proposal-status-dialog.component';
import { QuoteListComponent } from './quote-list.component';

// O nome da etapa viaja como texto do AutoLAB e o reconhecimento é por tabela,
// então etapa que falta na tabela não dá erro de compilação: ela escorrega para
// o fallback e a tela passa a mentir a situação da solicitação.
const ETAPA_ESPERADA_POR_NOME: ReadonlyArray<readonly [string, SituacaoDaSolicitacao]> = [
  ['Qualificação', 'qualificacao'],
  ['Em análise pela área', 'analise-da-area'],
  ['Elaborando proposta', 'elaborando-proposta'],
  ['Negociação', 'negociacao'],
  ['Aprovado pelo cliente', 'aprovado-pelo-cliente'],
  ['Aguardando entrega da amostra', 'aguardando-amostra'],
  ['Fechado ganho', 'recebido'],
  ['Protocolado', 'protocolado'],
  ['Em execução', 'em-execucao'],
  ['Incompleto', 'incompleto'],
  ['Completo', 'completo'],
  ['Validado', 'validado'],
  ['Finalizado', 'finalizado'],
  ['Elaborando relatório', 'elaborando-relatorio'],
  ['Relatório publicado', 'relatorio-publicado'],
  ['Fechado perdido', 'cancelado'],
  ['Fechado recusado Lactec', 'recusado'],
];

function orcamentoComEtapa(indice: number, nomeDaEtapa: string): Quote {
  return {
    id: `quote-${indice}`,
    code: `EAQ_2026_${45000 + indice}_V_1`,
    name: `Proposta ${indice}`,
    status: 'Em Andamento',
    stage: nomeDaEtapa,
    description: `Proposta ${indice}`,
    companyName: 'Empresa Exemplo Alfa Ltda',
    externalContactName: 'Fulano de Tal',
    externalContactEmail: 'fulano@exemplo.com',
    totalPrice: 1000 + indice,
    createdDate: '2026-05-05',
  };
}

const TODAS_AS_ETAPAS: ReadonlyArray<Quote> = ETAPA_ESPERADA_POR_NOME.map(([nome], indice) =>
  orcamentoComEtapa(indice + 1, nome),
);

function montarListagem(quotes: ReadonlyArray<Quote> = TODAS_AS_ETAPAS) {
  const dadosAbertos: ProposalStatusDialogData[] = [];
  const destinos: unknown[][] = [];

  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      { provide: QuotesService, useValue: { getQuotes: () => of(quotes) } },
      { provide: LoginService, useValue: { currentUser: () => 'Cliente Lactec' } },
      {
        provide: AppDialogService,
        useValue: {
          open: (_componente: unknown, data: ProposalStatusDialogData) => {
            dadosAbertos.push(data);
            return { afterClosed: () => of(undefined) };
          },
        },
      },
    ],
  });

  spyOn(TestBed.inject(Router), 'navigate').and.callFake((comandos: unknown[]) => {
    destinos.push(comandos);
    return Promise.resolve(true);
  });

  const fixture = TestBed.createComponent(QuoteListComponent);
  fixture.detectChanges();
  return { fixture, componente: fixture.componentInstance, dadosAbertos, destinos };
}

function rotuloDaEtapa(situacao: SituacaoDaSolicitacao): string {
  const etapa = LINHA_DE_ETAPAS.find((candidata) => candidata.chave === situacao);
  if (!etapa) throw new Error(`Etapa sem rótulo na linha: ${situacao}`);
  return etapa.rotulo;
}

describe('QuoteListComponent, reconhecimento da etapa', () => {
  it('cada nome de etapa do AutoLAB vira a sua própria situação', () => {
    const { componente } = montarListagem();

    const reconhecidas = componente.quotes().map((quote) => quote.status);

    expect(reconhecidas).toEqual(ETAPA_ESPERADA_POR_NOME.map(([, situacao]) => situacao));
  });

  it('nenhuma etapa escorrega para qualificação por falta de tabela', () => {
    const { componente } = montarListagem();

    const emQualificacao = componente
      .quotes()
      .filter((quote) => quote.status === 'qualificacao');

    expect(emQualificacao.length).toBe(1);
  });

  it('o modal de status abre na etapa do orçamento, e não na primeira do recorte', () => {
    const { componente, dadosAbertos } = montarListagem();
    const etapasDoLaboratorio: ReadonlyArray<SituacaoDaSolicitacao> = [
      'protocolado',
      'em-execucao',
      'incompleto',
    ];

    for (const situacao of etapasDoLaboratorio) {
      const quote = componente.quotes().find((item) => item.status === situacao);
      if (!quote) throw new Error(`Orçamento ausente para a situação ${situacao}`);
      componente.checkStatus(quote);
    }

    const etapasDestacadas = dadosAbertos.map((data) => data.steps[data.currentIndex].label);
    expect(etapasDestacadas).toEqual(etapasDoLaboratorio.map(rotuloDaEtapa));
  });

  // O processo é um só, e o stepper precisa da linha inteira para manter a
  // etapa atual no meio da janela. Recortar a linha aqui, por grupo, jogava a
  // etapa atual para a borda do recorte e escondia o que vinha depois dela.
  it('o modal recebe a linha inteira de etapas, e a atual por índice absoluto', () => {
    const { componente, dadosAbertos } = montarListagem();
    const recebido = componente.quotes().find((item) => item.status === 'recebido');
    if (!recebido) throw new Error('Orçamento em recebido ausente na massa de teste');

    componente.checkStatus(recebido);

    expect(dadosAbertos[0].steps.length).toBe(LINHA_DE_ETAPAS.length);
    expect(dadosAbertos[0].currentIndex).toBe(indiceDaEtapa('recebido'));
  });

  // O orçamento nasce quando alguém termina de escrever a proposta: antes
  // disso o botão prometeria um documento que o Salesforce não tem.
  it('só oferece o download a partir da negociação', () => {
    const { componente, dadosAbertos } = montarListagem();
    const semProposta: ReadonlyArray<SituacaoDaSolicitacao> = [
      'qualificacao',
      'analise-da-area',
      'elaborando-proposta',
    ];
    const comProposta: ReadonlyArray<SituacaoDaSolicitacao> = [
      'negociacao',
      'aprovado-pelo-cliente',
      'recebido',
      'completo',
    ];

    for (const situacao of [...semProposta, ...comProposta]) {
      const quote = componente.quotes().find((item) => item.status === situacao);
      if (!quote) throw new Error(`Orçamento ausente para a situação ${situacao}`);
      componente.checkStatus(quote);
    }

    expect(dadosAbertos.map((data) => data.hasProposal)).toEqual([
      false,
      false,
      false,
      true,
      true,
      true,
      true,
    ]);
  });

  // Nas etapas da proposta o cliente não vê documento nenhum: o que ele tem é
  // o aviso explicando em que pé está o pedido dele.
  it('toda etapa que o cliente acompanha explica o que está acontecendo', () => {
    const { componente, dadosAbertos } = montarListagem();
    const etapasComAviso: ReadonlyArray<SituacaoDaSolicitacao> = [
      'qualificacao',
      'analise-da-area',
      'elaborando-proposta',
      'negociacao',
      'aprovado-pelo-cliente',
      'aguardando-amostra',
      'elaborando-relatorio',
    ];

    for (const situacao of etapasComAviso) {
      const quote = componente.quotes().find((item) => item.status === situacao);
      if (!quote) throw new Error(`Orçamento ausente para a situação ${situacao}`);
      componente.checkStatus(quote);
    }

    const semAviso = dadosAbertos.filter((data) => !data.messageTitle || !data.message);
    expect(semAviso).toEqual([]);
  });

  // O botão leva ao orçamento, e só há o que fazer lá em duas etapas: aprovar
  // ou recusar a proposta, e confirmar que a amostra foi despachada.
  it('o botão de ir ao orçamento só aparece onde há ação a tomar lá dentro', () => {
    const { componente, dadosAbertos } = montarListagem();
    const comAcao: ReadonlyArray<SituacaoDaSolicitacao> = ['negociacao', 'aprovado-pelo-cliente'];

    for (const quote of componente.quotes()) {
      componente.checkStatus(quote);
    }

    const comBotao = dadosAbertos
      .filter((data) => data.callToAction)
      .map((data) => data.steps[data.currentIndex].key);
    expect(comBotao).toEqual([...comAcao]);
  });

  // A caixa sem etiqueta para no recebimento, então o lembrete acompanha o
  // cliente do aceite até a amostra chegar; o endereço, só enquanto ele ainda
  // não despachou.
  it('a orientação de envio aparece no aceite com endereço e segue até a amostra chegar', () => {
    const { componente, dadosAbertos } = montarListagem();

    for (const quote of componente.quotes()) {
      componente.checkStatus(quote);
    }

    const orientacoes = dadosAbertos
      .filter((data) => data.shipping)
      .map((data) => [data.steps[data.currentIndex].key, data.shipping]);
    expect(orientacoes).toEqual([
      ['aprovado-pelo-cliente', 'address-and-labeling'],
      ['aguardando-amostra', 'labeling'],
    ]);
  });

  it('o modal do orçamento encerrado anuncia o encerramento em vez de uma etapa', () => {
    const { componente, dadosAbertos } = montarListagem();
    const cancelado = componente.quotes().find((item) => item.status === 'cancelado');
    if (!cancelado) throw new Error('Orçamento cancelado ausente na massa de teste');

    componente.checkStatus(cancelado);

    expect(dadosAbertos[0].titulo).toBe('Solicitação encerrada');
    expect(dadosAbertos[0].message).toContain('encerrada');
  });

  // Com o laudo publicado não há mais etapa a acompanhar, então o botão deixa
  // de abrir o modal e leva aos laudos daquele orçamento.
  it('o orçamento com relatório publicado vai para os relatórios dele, sem abrir modal', () => {
    const { componente, dadosAbertos, destinos } = montarListagem();
    const publicado = componente.quotes().find((item) => item.status === 'relatorio-publicado');
    if (!publicado) throw new Error('Orçamento com relatório publicado ausente na massa de teste');

    componente.checkStatus(publicado);

    expect(destinos).toEqual([['/cliente/relatorios', publicado.id]]);
    expect(dadosAbertos).toEqual([]);
  });
});

describe('QuoteListComponent, link para o orçamento', () => {
  // Abrir o orçamento de uma proposta que ninguém escreveu leva a uma tela sem
  // orçamento nenhum: nessas etapas o cartão oferece apenas a situação.
  it('o cartão das etapas sem proposta escrita não leva ao orçamento', () => {
    const semOrcamento: ReadonlyArray<SituacaoDaSolicitacao> = [
      'qualificacao',
      'analise-da-area',
      'elaborando-proposta',
    ];
    const { fixture, componente } = montarListagem();

    const cartoes = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('.orcamento-card'),
    );
    const levaAoOrcamento = cartoes.map(
      (cartao) => !!cartao.querySelector('a[href^="/cliente/orcamentos/"]'),
    );

    expect(cartoes.length).toBe(componente.visibleQuotes().length);
    expect(levaAoOrcamento).toEqual(
      componente.visibleQuotes().map((quote) => !semOrcamento.includes(quote.status)),
    );
  });
});
