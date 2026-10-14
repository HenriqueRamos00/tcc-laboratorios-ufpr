import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import type { Quote } from '@/app/model/quote';
import type { PublishedReport } from '@/app/model/report';
import { QuotesService } from '@/app/services/quotes.service';
import { ReportsService } from '@/app/services/reports.service';

import { ReportListComponent } from './report-list.component';

function laudo(code: string, protocol: string): PublishedReport {
  return {
    id: `laudo-${code}`,
    code,
    protocol,
    sample: 'Transformador TR-01',
    laboratory: 'Lab. de Óleos',
    publishedOn: '07/03/2026',
  };
}

// Laudos de dois orçamentos diferentes: a aba de todos tem que trazer os dois,
// e não só os do primeiro orçamento que a API devolver.
const TODOS_OS_LAUDOS: readonly PublishedReport[] = [
  laudo('LACTEC-00307/2026', '00307/2026'),
  laudo('LACTEC-00308/2026', '00307/2026'),
  laudo('LACTEC-00412/2026', '00412/2026'),
];

function quote(id: string, code: string, stage: string): Quote {
  return {
    id,
    code,
    name: `Proposta ${code}`,
    status: 'Finalizado',
    stage,
    description: `Proposta ${code}`,
    companyName: 'Empresa Exemplo Alfa Ltda',
    externalContactName: 'Fulano de Tal',
    externalContactEmail: 'fulano@exemplo.com',
    totalPrice: 1000,
    createdDate: '2026-05-05T09:00:00.000+0000',
  };
}

const ORCAMENTOS: ReadonlyArray<Quote> = [
  quote('quote-15', 'EAQ_2026_45496_V_1', 'Relatório publicado'),
  quote('quote-18', 'EAQ_2026_45499_V_1', 'Relatório publicado'),
  quote('quote-10', 'EAQ_2026_45491_V_1', 'Em execução'),
  quote('quote-16', 'EAQ_2026_45497_V_1', 'Fechado perdido'),
];

function montarTela() {
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      { provide: QuotesService, useValue: { getQuotes: () => of([...ORCAMENTOS]) } },
      { provide: ReportsService, useValue: { publishedReports: () => of(TODOS_OS_LAUDOS) } },
    ],
  });

  const fixture = TestBed.createComponent(ReportListComponent);
  fixture.detectChanges();
  const tela: HTMLElement = fixture.nativeElement;

  function abrirAba(id: string): void {
    tela.querySelector<HTMLButtonElement>(`#${id}`)!.click();
    fixture.detectChanges();
  }

  return { fixture, tela, abrirAba };
}

describe('ReportListComponent, as duas portas de entrada dos laudos', () => {
  it('a aba de todos os relatórios traz os laudos de todos os orçamentos', () => {
    const { tela } = montarTela();

    const codigos = Array.from(
      tela.querySelectorAll('#panel-reports tbody tr td:first-child'),
    ).map((celula) => celula.textContent?.trim());

    expect(codigos).toEqual(TODOS_OS_LAUDOS.map((relatorio) => relatorio.code));
  });

  it('a aba de orçamentos lista só os que têm relatório publicado', () => {
    const { tela, abrirAba } = montarTela();

    abrirAba('tab-quotes');
    const exibidos = Array.from(
      tela.querySelectorAll('#panel-quotes article .quote-card-field__value'),
    )
      .map((campo) => campo.textContent?.trim())
      .filter((texto) => texto?.startsWith('EAQ_'));

    expect(exibidos).toEqual(['EAQ_2026_45496_V_1', 'EAQ_2026_45499_V_1']);
  });

  it('o botão de cada orçamento leva aos relatórios daquele orçamento', () => {
    const { tela, abrirAba } = montarTela();

    abrirAba('tab-quotes');
    const destinos = Array.from(tela.querySelectorAll('#panel-quotes article a')).map((link) =>
      link.getAttribute('href'),
    );

    expect(destinos).toEqual(['/cliente/relatorios/quote-15', '/cliente/relatorios/quote-18']);
  });

  // As duas abas são listagem: a linha de etapas é da tela de um orçamento só,
  // porque ali ela descreve um processo, e aqui descreveria vários ao mesmo tempo.
  it('nenhuma das duas abas mostra a linha de etapas', () => {
    const { tela, abrirAba } = montarTela();

    const naAbaDeLaudos = tela.querySelector('app-status-stepper');
    abrirAba('tab-quotes');
    const naAbaDeOrcamentos = tela.querySelector('app-status-stepper');

    expect(naAbaDeLaudos).toBeNull();
    expect(naAbaDeOrcamentos).toBeNull();
  });
});
