import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import type { AnalysisConclusion } from '@/app/model/report';
import { ReportsService } from '@/app/services/reports.service';

import { QuoteReportsComponent } from './quote-reports.component';

function conclusao(quoteId: string, quoteCode: string, protocol: string): AnalysisConclusion {
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
        publishedOn: '07/03/2026',
      },
    ],
  };
}

const CONCLUSAO_POR_ORCAMENTO = new Map<string, AnalysisConclusion>([
  ['quote-15', conclusao('quote-15', 'EAQ_2026_45496_V_1', '00307/2026')],
  ['quote-18', conclusao('quote-18', 'EAQ_2026_45499_V_1', '00412/2026')],
]);

function montarTela(quoteId: string) {
  const pedidos: string[] = [];

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
        },
      },
    ],
  });

  const fixture = TestBed.createComponent(QuoteReportsComponent);
  fixture.componentRef.setInput('quoteId', quoteId);
  fixture.detectChanges();

  return { fixture, tela: fixture.nativeElement as HTMLElement, pedidos };
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
});
