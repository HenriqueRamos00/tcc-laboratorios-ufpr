import { fakeAsync, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import {
  PAGE_SIZE_WITHOUT_SCROLL_AREA,
  renderMeasuredTable,
} from '@shared/components/data-table/data-table.spec-util';

import { DEFAULT_PAGE_SIZE, type PageRequest } from '@/app/model/page';
import type { Quote } from '@/app/model/quote';
import type { Report } from '@/app/model/report';
import { QuotesService } from '@/app/services/quotes.service';
import { ReportsService } from '@/app/services/reports.service';

import { ReportListComponent } from './report-list.component';

const PAGE_SIZE = PAGE_SIZE_WITHOUT_SCROLL_AREA;

function laudo(
  identification: string,
  hasSignedDocument = true,
  laboratory: string | null = 'Óleos',
): Report {
  return {
    id: `id-${identification}`,
    identification,
    requestNumber: 'S-1',
    quoteCode: '2026_45496',
    sampleId: identification.replace('LACTEC-', ''),
    sampleName: null,
    laboratory,
    client: 'EMPRESA DE ENERGIA',
    status: 'Publicado',
    scheduledDate: null,
    issueDate: '2026-03-07',
    publicationDate: '2026-03-08',
    hasSignedDocument,
  };
}

const TODOS_OS_LAUDOS: readonly Report[] = [
  laudo('LACTEC-00307/2026'),
  laudo('LACTEC-00308/2026', false),
  laudo('LACTEC-00412/2026', true, null),
];

function quote(id: string, code: string, stage: string, laboratoryStage?: string): Quote {
  return {
    id,
    code,
    name: `Proposta ${code}`,
    status: 'Finalizado',
    stage,
    laboratoryStage,
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
  quote('quote-16', 'EAQ_2026_45497_V_1', 'Fechado perdido', 'relatorio-publicado'),
  quote('quote-20', 'SET_2026_47711_V_1', 'Aprovado pelo cliente', 'relatorio-publicado'),
];

function montarTela(total = TODOS_OS_LAUDOS.length) {
  const pedidos: PageRequest[] = [];
  const pedidosDeOrcamentos: PageRequest[] = [];
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      {
        provide: QuotesService,
        useValue: {
          list: (request: PageRequest) => {
            pedidosDeOrcamentos.push(request);
            return of({ items: [...ORCAMENTOS], total: ORCAMENTOS.length, page: request.page, pageSize: request.pageSize });
          },
        },
      },
      {
        provide: ReportsService,
        useValue: {
          list: (request: PageRequest) => {
            pedidos.push(request);
            return of({ items: TODOS_OS_LAUDOS, total, page: request.page, pageSize: request.pageSize });
          },
        },
      },
    ],
  });

  const fixture = TestBed.createComponent(ReportListComponent);
  renderMeasuredTable(fixture);
  fixture.detectChanges();
  const tela: HTMLElement = fixture.nativeElement;

  function abrirAba(id: string): void {
    tela.querySelector<HTMLButtonElement>(`#${id}`)!.click();
    fixture.detectChanges();
  }

  return { fixture, tela, abrirAba, pedidos, pedidosDeOrcamentos };
}

describe('ReportListComponent, as duas portas de entrada dos laudos', () => {
  it('a aba de todos os relatórios traz os laudos que a API devolve', fakeAsync(() => {
    const { tela, pedidos } = montarTela();

    const codigos = Array.from(
      tela.querySelectorAll('#panel-reports tbody tr td:first-child'),
    ).map((celula) => celula.textContent?.trim());

    expect(pedidos).toEqual([{ page: 1, pageSize: PAGE_SIZE }]);
    expect(codigos).toEqual(TODOS_OS_LAUDOS.map((relatorio) => relatorio.identification ?? undefined));
  }));

  it('a coluna Amostra mostra a amostra, e a coluna Laboratório mostra o laboratório', fakeAsync(() => {
    const { tela } = montarTela();

    const amostras = Array.from(
      tela.querySelectorAll('#panel-reports tbody tr td:nth-child(3)'),
    ).map((celula) => celula.textContent?.trim());
    const laboratorios = Array.from(
      tela.querySelectorAll('#panel-reports tbody tr td:nth-child(4)'),
    ).map((celula) => celula.textContent?.trim());

    expect(amostras).toEqual(['00307/2026', '00308/2026', '00412/2026']);
    expect(laboratorios).toEqual(['Óleos', 'Óleos', 'Dados não disponíveis']);
  }));

  it('só habilita o download do laudo que tem PDF assinado', fakeAsync(() => {
    const { tela } = montarTela();

    const habilitados = Array.from(
      tela.querySelectorAll<HTMLButtonElement>('#panel-reports tbody button:last-child'),
    ).map((botao) => !botao.disabled);

    expect(habilitados).toEqual([true, false, true]);
  }));

  it('pede ao servidor a página seguinte', fakeAsync(() => {
    const { fixture, tela, pedidos } = montarTela(120);

    const proximo = Array.from(tela.querySelectorAll('app-pagination button')).find((botao) =>
      botao.textContent?.includes('Próximo'),
    ) as HTMLButtonElement;
    proximo.click();
    renderMeasuredTable(fixture);

    expect(pedidos.at(-1)).toEqual({ page: 2, pageSize: PAGE_SIZE });
  }));

  it('a busca sem resultado na página carregada orienta a revisar o termo, dentro da tabela', fakeAsync(() => {
    const { fixture, tela } = montarTela();

    const campo = tela.querySelector('#panel-reports input') as HTMLInputElement;
    campo.value = 'nao-existe';
    campo.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(tela.querySelector('#panel-reports tbody')?.textContent).toContain(
      'Nenhum relatório encontrado',
    );
  }));

  it('os orçamentos, que são cartões, continuam vindo em páginas de tamanho fixo', fakeAsync(() => {
    const { pedidosDeOrcamentos } = montarTela();

    expect(pedidosDeOrcamentos).toEqual([{ page: 1, pageSize: DEFAULT_PAGE_SIZE }]);
  }));

  it('a aba de orçamentos lista só os que têm relatório publicado', fakeAsync(() => {
    const { tela, abrirAba } = montarTela();

    abrirAba('tab-quotes');
    const exibidos = Array.from(
      tela.querySelectorAll('#panel-quotes article span'),
    )
      .map((campo) => campo.textContent?.trim())
      .filter((texto) => /^(EAQ|SET)_/.test(texto ?? ''));

    expect(exibidos).toEqual(['EAQ_2026_45496_V_1', 'EAQ_2026_45499_V_1', 'SET_2026_47711_V_1']);
  }));

  it('o botão de cada orçamento leva aos relatórios daquele orçamento', fakeAsync(() => {
    const { tela, abrirAba } = montarTela();

    abrirAba('tab-quotes');
    const destinos = Array.from(tela.querySelectorAll('#panel-quotes article a')).map((link) =>
      link.getAttribute('href'),
    );

    expect(destinos).toEqual([
      '/cliente/relatorios/quote-15',
      '/cliente/relatorios/quote-18',
      '/cliente/relatorios/quote-20',
    ]);
  }));

  // As duas abas são listagem: a linha de etapas é da tela de um orçamento só,
  // porque ali ela descreve um processo, e aqui descreveria vários ao mesmo tempo.
  it('nenhuma das duas abas mostra a linha de etapas', fakeAsync(() => {
    const { tela, abrirAba } = montarTela();

    const naAbaDeLaudos = tela.querySelector('app-status-stepper');
    abrirAba('tab-quotes');
    const naAbaDeOrcamentos = tela.querySelector('app-status-stepper');

    expect(naAbaDeLaudos).toBeNull();
    expect(naAbaDeOrcamentos).toBeNull();
  }));
});
