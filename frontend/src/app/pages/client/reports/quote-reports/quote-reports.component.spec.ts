import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import type { AnalysisConclusion } from '@/app/model/report';
import { ReportsService } from '@/app/services/reports.service';

import { QuoteReportsComponent } from './quote-reports.component';

function conclusao(
  quoteId: string,
  quoteCode: string,
  protocol: string,
  publishedOn = '08/11/2026',
): AnalysisConclusion {
  return {
    quoteId,
    quoteCode,
    protocol,
    testType: 'Ensaios Físico-Químicos em Óleo Isolante',
    summary: 'A análise foi concluída e validada pela curadoria laboratorial.',
    reports: [
      {
        id: `${quoteId}-1`,
        code: `LACTEC-${protocol}`,
        protocol,
        sample: 'Transformador TR-01',
        laboratory: 'Lab. de Óleos',
        publishedOn,
      },
    ],
  };
}

const CONCLUSAO_POR_ORCAMENTO = new Map<string, AnalysisConclusion | null>([
  ['quote-15', conclusao('quote-15', 'EAQ_2026_45496_V_1', '00307/2026')],
  ['quote-18', conclusao('quote-18', 'EAQ_2026_45499_V_1', '00412/2026')],
  ['quote-10', null],
  ['quote-20', (() => {
    const conclusion = conclusao('quote-20', 'ORC-2026-0091', 'L-45490-2026-X');
    return {
      ...conclusion,
      reports: [
        ...conclusion.reports,
        { ...conclusion.reports[0], id: 'quote-20-2', code: '805' },
      ],
      testType: 'Análise cromatográfica; Análise físico-química',
      summary: '2 relatórios publicados disponíveis para consulta e download.',
    };
  })()],
  ['quote-21', conclusao('quote-21', 'ORC-2026-0092', 'L-45491-2026-X', '07/11/2026')],
]);

function montarTela(quoteId: string) {
  const pedidos: string[] = [];
  const downloadReport = jasmine.createSpy('downloadReport').and.returnValue(of(new Blob(['%PDF-1.4'])));

  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      {
        provide: ReportsService,
        useValue: {
          analysisConclusion: (id: string) => {
            pedidos.push(id);
            return of(CONCLUSAO_POR_ORCAMENTO.get(id) ?? null);
          },
          downloadReport,
        },
      },
    ],
  });

  const fixture = TestBed.createComponent(QuoteReportsComponent);
  fixture.componentRef.setInput('quoteId', quoteId);
  fixture.detectChanges();

  return { fixture, tela: fixture.nativeElement as HTMLElement, pedidos, downloadReport };
}

function protocolosNaTabela(tela: HTMLElement): (string | undefined)[] {
  return Array.from(tela.querySelectorAll('tbody tr td:nth-child(2)')).map((celula) =>
    celula.textContent?.trim(),
  );
}

describe('QuoteReportsComponent, os laudos de um orçamento só', () => {
  it('pede ao serviço o orçamento que o endereço nomeia e mostra só os laudos dele', () => {
    const { tela, pedidos } = montarTela('quote-18');

    expect(pedidos).toEqual(['quote-18']);
    expect(protocolosNaTabela(tela)).toEqual(['00412/2026']);
  });

  it('apresenta a data de publicação recebida da API', () => {
    const { tela } = montarTela('quote-18');

    const publicacao = tela.querySelector('tbody tr td:nth-child(5)');

    expect(publicacao?.textContent?.trim()).toBe('08/11/2026');
  });

  it('exibe a data de publicação formatada recebida da API', () => {
    const { tela } = montarTela('quote-21');

    const publicacao = tela.querySelector('tbody tr td:nth-child(5)');

    expect(publicacao?.textContent?.trim()).toBe('07/11/2026');
  });

  // O router reaproveita a instância quando só o identificador muda: sem
  // recarregar, a tela fica mostrando os laudos do orçamento anterior.
  it('troca os laudos quando o endereço passa a nomear outro orçamento', () => {
    const { fixture, tela, pedidos } = montarTela('quote-18');

    fixture.componentRef.setInput('quoteId', 'quote-15');
    fixture.detectChanges();

    expect(pedidos).toEqual(['quote-18', 'quote-15']);
    expect(protocolosNaTabela(tela)).toEqual(['00307/2026']);
  });

  it('a etapa destacada é a de relatório publicado', () => {
    const { tela } = montarTela('quote-15');

    const atual = tela.querySelector('.status-stepper__step[data-state="active"]');

    expect(atual?.textContent?.trim()).toContain('Relatório publicado');
  });

  it('orçamento sem laudo publicado anuncia a espera, em vez de uma tabela vazia', () => {
    const { tela } = montarTela('quote-10');

    expect(tela.querySelector('tbody')).toBeNull();
    expect(tela.textContent).toContain('ainda não tem relatório publicado');
  });

  it('apresenta os tipos e o resumo enviados pelo backend', () => {
    const { tela } = montarTela('quote-20');

    expect(tela.textContent).toContain('ID do Protocolo: L-45490-2026-X');
    expect(tela.textContent).not.toContain('A fonte atual não fornece');
    expect(tela.textContent).toContain('Análise cromatográfica; Análise físico-química');
    expect(tela.textContent).toContain('2 relatórios publicados disponíveis para consulta e download.');
    expect(tela.textContent).not.toContain('Não informado');
  });

  it('conecta o botão ao download do PDF do relatório', () => {
    spyOn(URL, 'createObjectURL').and.returnValue('blob:relatorio');
    spyOn(HTMLAnchorElement.prototype, 'click');
    const { tela, downloadReport } = montarTela('quote-15');

    tela.querySelector<HTMLButtonElement>('tbody button')!.click();

    expect(downloadReport).toHaveBeenCalledWith('quote-15-1');
    expect(URL.createObjectURL).toHaveBeenCalled();
  });
});
