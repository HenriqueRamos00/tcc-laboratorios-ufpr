import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import type { Quote } from '@/app/model/quote';
import type { Report } from '@/app/model/report';
import type { ReportQuery } from '@/app/services/reports.service';
import { QuotesService } from '@/app/services/quotes.service';
import { ReportsService } from '@/app/services/reports.service';

import { QuoteReportsComponent } from './quote-reports.component';

function quote(id: string, code: string, stage: string): Quote {
  return {
    id, code, name: `Proposta ${code}`, status: 'Finalizado', stage, description: '',
    companyName: 'EMPRESA DE ENERGIA', externalContactName: '', externalContactEmail: '',
    totalPrice: 0, createdDate: null,
  };
}

function laudo(identification: string, quoteCode: string): Report {
  return {
    id: `id-${identification}`,
    identification,
    requestNumber: 'S-1',
    quoteCode,
    sampleId: null,
    sampleName: 'Transformador TR-01',
    client: 'EMPRESA DE ENERGIA',
    status: 'Publicado',
    scheduledDate: null,
    issueDate: '2026-03-07',
    publicationDate: '2026-03-08',
    hasSignedDocument: true,
  };
}

const ORCAMENTOS = new Map<string, Quote>([
  ['quote-15', quote('quote-15', 'EAQ_2026_45496_V_1', 'Relatório publicado')],
  ['quote-18', quote('quote-18', 'EAQ_2026_45499_V_1', 'Elaborando relatório')],
  ['quote-10', quote('quote-10', 'EAQ_2026_45491_V_1', 'Em execução')],
  ['quote-16', quote('quote-16', 'EAQ_2026_45497_V_1', 'Fechado perdido')],
]);

const LAUDOS_POR_CODIGO = new Map<string, Report[]>([
  ['EAQ_2026_45496_V_1', [laudo('LACTEC-00307/2026', 'EAQ_2026_45496_V_1')]],
  ['EAQ_2026_45499_V_1', [laudo('LACTEC-00412/2026', 'EAQ_2026_45499_V_1')]],
]);

function montarTela(quoteId: string) {
  const pedidos: ReportQuery[] = [];

  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      {
        provide: QuotesService,
        useValue: {
          getQuoteById: (id: string) => {
            const found = ORCAMENTOS.get(id);
            return found ? of(found) : throwError(() => new Error('404'));
          },
        },
      },
      {
        provide: ReportsService,
        useValue: {
          list: (query: ReportQuery) => {
            pedidos.push(query);
            const items = LAUDOS_POR_CODIGO.get(query.quoteCode ?? '') ?? [];
            return of({ items, total: items.length, page: query.page, pageSize: query.pageSize });
          },
        },
      },
    ],
  });

  const fixture = TestBed.createComponent(QuoteReportsComponent);
  fixture.componentRef.setInput('quoteId', quoteId);
  fixture.detectChanges();

  return { fixture, tela: fixture.nativeElement as HTMLElement, pedidos };
}

function relatoriosNaTabela(tela: HTMLElement): (string | undefined)[] {
  return Array.from(tela.querySelectorAll('tbody tr td:first-child')).map((celula) =>
    celula.textContent?.trim(),
  );
}

describe('QuoteReportsComponent, os laudos de um orçamento só', () => {
  it('pede à API os laudos pelo código do orçamento que o endereço nomeia', () => {
    const { tela, pedidos } = montarTela('quote-18');

    expect(pedidos).toEqual([{ quoteCode: 'EAQ_2026_45499_V_1', page: 1, pageSize: 50 }]);
    expect(relatoriosNaTabela(tela)).toEqual(['LACTEC-00412/2026']);
  });

  it('troca os laudos quando o endereço passa a nomear outro orçamento', () => {
    const { fixture, tela, pedidos } = montarTela('quote-18');

    fixture.componentRef.setInput('quoteId', 'quote-15');
    fixture.detectChanges();

    expect(pedidos.map((pedido) => pedido.quoteCode)).toEqual([
      'EAQ_2026_45499_V_1',
      'EAQ_2026_45496_V_1',
    ]);
    expect(relatoriosNaTabela(tela)).toEqual(['LACTEC-00307/2026']);
  });

  it('a etapa destacada é a etapa real do orçamento', () => {
    const publicado = montarTela('quote-15').tela;
    expect(publicado.querySelector('.status-stepper__step[data-state="active"]')?.textContent)
      .toContain('Relatório publicado');

    TestBed.resetTestingModule();
    const elaborando = montarTela('quote-18').tela;
    expect(elaborando.querySelector('.status-stepper__step[data-state="active"]')?.textContent)
      .toContain('Elaborando relatório');
  });

  it('orçamento encerrado não mostra a linha de etapas', () => {
    const { tela } = montarTela('quote-16');

    expect(tela.querySelector('app-status-stepper')).toBeNull();
  });

  it('tipo de ensaio, resumo e laboratório, sem coluna no AutoLAB, aparecem como não disponíveis', () => {
    const { tela } = montarTela('quote-15');

    expect(tela.textContent).toContain('Tipo de ensaio: Dados não disponíveis');
    expect(tela.textContent).toContain('Resumo da análise: Dados não disponíveis');
    expect(tela.querySelector('tbody tr td:nth-child(4)')?.textContent?.trim())
      .toBe('Dados não disponíveis');
  });

  it('orçamento sem laudo publicado anuncia a espera, em vez de uma tabela vazia', () => {
    const { tela } = montarTela('quote-10');

    expect(tela.querySelector('tbody')).toBeNull();
    expect(tela.textContent).toContain('ainda não tem relatório publicado');
  });

  it('orçamento que a API não entrega vira mensagem de falha', () => {
    const { tela, pedidos } = montarTela('quote-99');

    expect(pedidos).toEqual([]);
    expect(tela.textContent).toContain('Não foi possível carregar os relatórios');
  });
});
