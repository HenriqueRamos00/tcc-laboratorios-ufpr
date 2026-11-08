import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import type { Quote } from '@/app/model/quote';
import type { PublishedReport } from '@/app/model/report';
import { QuotesService } from '@/app/services/quotes.service';
import { ReportsService } from '@/app/services/reports.service';

import { ReportListComponent } from './report-list.component';

function laudo(code: string, protocol: string, publishedOn = '08/11/2026'): PublishedReport {
  return {
    id: `laudo-${code}`,
    code,
    protocol,
    sample: 'Transformador TR-01',
    laboratory: 'Lab. de Óleos',
    publishedOn,
  };
}

// Laudos de dois orçamentos diferentes: a aba de todos tem que trazer os dois,
// e não só os do primeiro orçamento que a API devolver.
const TODOS_OS_LAUDOS: readonly PublishedReport[] = [
  laudo('LACTEC-00307/2026', '00307/2026'),
  laudo('LACTEC-00308/2026', '00307/2026'),
  laudo('L-45490-2026-X', 'L-45490-2026-X', '07/11/2026'),
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
    protocol: 'L-45496-2026-X',
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
  const downloadReport = jasmine.createSpy('downloadReport').and.returnValue(of(new Blob(['%PDF-1.4'])));

  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      { provide: QuotesService, useValue: { getQuotes: () => of([...ORCAMENTOS]) } },
      {
        provide: ReportsService,
        useValue: { publishedReports: () => of(TODOS_OS_LAUDOS), downloadReport },
      },
    ],
  });

  const fixture = TestBed.createComponent(ReportListComponent);
  fixture.detectChanges();
  const tela: HTMLElement = fixture.nativeElement;

  function abrirAba(id: string): void {
    tela.querySelector<HTMLButtonElement>(`#${id}`)!.click();
    fixture.detectChanges();
  }

  return { fixture, tela, abrirAba, downloadReport };
}

describe('ReportListComponent, as duas portas de entrada dos laudos', () => {
  it('a aba de todos os relatórios traz os laudos de todos os orçamentos', () => {
    const { tela } = montarTela();

    const codigos = Array.from(
      tela.querySelectorAll('#panel-reports tbody tr td:first-child'),
    ).map((celula) => celula.textContent?.trim());

    expect(codigos).toEqual(TODOS_OS_LAUDOS.map((relatorio) => relatorio.code));
  });

  it('exibe a data de publicação formatada recebida da API', () => {
    const { tela } = montarTela();

    const publicacao = tela.querySelector('#panel-reports tbody tr:nth-child(3) td:nth-child(5)');

    expect(publicacao?.textContent?.trim()).toBe('07/11/2026');
  });

  it('preserva a data civil Lactec recebida da API', () => {
    const { tela } = montarTela();

    const publicacao = tela.querySelector('#panel-reports tbody tr:first-child td:nth-child(5)');

    expect(publicacao?.textContent?.trim()).toBe('08/11/2026');
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

  it('pede o arquivo pelo identificador próprio do relatório ao baixar', () => {
    spyOn(URL, 'createObjectURL').and.returnValue('blob:relatorio');
    spyOn(HTMLAnchorElement.prototype, 'click');
    const { tela, downloadReport } = montarTela();

    tela.querySelector<HTMLButtonElement>('#panel-reports tbody button')!.click();

    expect(downloadReport).toHaveBeenCalledWith('laudo-LACTEC-00307/2026');
    expect(URL.createObjectURL).toHaveBeenCalled();
  });
});
