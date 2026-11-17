import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { fakeAsync, TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter } from '@angular/router';
import { NEVER, of, Subject } from 'rxjs';

import { renderMeasuredTable } from '@shared/components/data-table/data-table.spec-util';

import { API_URL } from '@/app/environment/env';
import type { HealthIndicators } from '@/app/model/health';
import type { Page, PageRequest } from '@/app/model/page';
import type { Quote } from '@/app/model/quote';
import { AppDialogService } from '@/app/services/app-dialog.service';
import { HEALTH_INDICATORS } from '@/app/services/fixtures/health-indicators.fixture';
import { HealthService } from '@/app/services/health.service';
import { LoginService } from '@/app/services/login.service';
import { QuotesService } from '@/app/services/quotes.service';
import { ReportsService } from '@/app/services/reports.service';

import { EquipmentDetailComponent } from './equipment/detail/equipment-detail.component';
import { HealthIndicatorsComponent } from './equipment/health-indicators/health-indicators.component';
import { usePdfViewerStub } from './quotes/detail/pdf-viewer.spec-util';
import { QuoteDetailComponent } from './quotes/detail/quote-detail.component';
import { QuoteListComponent } from './quotes/quote-list.component';
import { QuoteReportsComponent } from './reports/quote-reports/quote-reports.component';
import { ReportListComponent } from './reports/report-list.component';

const QUOTE: Quote = {
  id: 'quote-15',
  code: 'EAQ_2026_45496_V_1',
  name: 'Ensaio em transformador de força',
  status: 'Em Andamento',
  stage: 'Relatório publicado',
  description: 'Análise físico-química e cromatografia',
  companyName: 'Empresa Exemplo Alfa Ltda',
  externalContactName: 'Fulano de Tal',
  externalContactEmail: 'fulano@exemplo.com',
  totalPrice: 4200,
  createdDate: '2026-05-05',
};

function pageOf(items: readonly Quote[], request: PageRequest, total = items.length): Page<Quote> {
  return { items: [...items], total, page: request.page, pageSize: request.pageSize };
}

function region(view: HTMLElement): HTMLElement | null {
  return view.querySelector<HTMLElement>('app-loading-region');
}

function expectSilentBusyRegion(view: HTMLElement): void {
  expect(region(view)?.getAttribute('aria-busy')).toBe('true');
  expect(region(view)?.firstElementChild?.getAttribute('aria-hidden')).toBe('true');
  expect(region(view)?.textContent?.trim()).toBe('');
  expect(view.textContent).not.toContain('Carregando');
}

describe('skeletons das telas do cliente', () => {
  describe('indicadores de saúde', () => {
    function mount(aba?: string) {
      const response = new Subject<HealthIndicators>();
      TestBed.configureTestingModule({
        providers: [
          provideRouter([]),
          provideHttpClient(),
          provideHttpClientTesting(),
          { provide: HealthService, useValue: { equipmentIndicators: () => response } },
        ],
      });
      const fixture = TestBed.createComponent(HealthIndicatorsComponent);
      fixture.componentRef.setInput('id', HEALTH_INDICATORS.equipmentId);
      if (aba) fixture.componentRef.setInput('aba', aba);
      fixture.detectChanges();
      return { fixture, response, view: fixture.nativeElement as HTMLElement };
    }

    it('desenha o painel físico-químico enquanto a aba pedida é a padrão', () => {
      const { view } = mount();

      expectSilentBusyRegion(view);
      expect(view.querySelector('[data-skeleton-panel]')?.getAttribute('data-skeleton-panel')).toBe(
        'fisico-quimico',
      );
    });

    it('desenha o painel de gases quando a URL já pede essa aba', () => {
      const { view } = mount('gases');

      expect(view.querySelector('[data-skeleton-panel]')?.getAttribute('data-skeleton-panel')).toBe(
        'criterios',
      );
    });

    it('recarregando com a sub-aba de métodos aberta, desenha a grade de figuras', () => {
      const { fixture, response, view } = mount('gases');

      response.next(HEALTH_INDICATORS);
      fixture.detectChanges();
      view.querySelector<HTMLButtonElement>('#aba-metodos')!.click();
      fixture.detectChanges();
      fixture.componentRef.setInput('reportId', 'outro-laudo');
      fixture.detectChanges();

      expect(view.querySelector('[data-skeleton-panel]')?.getAttribute('data-skeleton-panel')).toBe(
        'metodos',
      );
    });

    it('sai de cena quando os indicadores chegam', () => {
      const { fixture, response, view } = mount();

      response.next(HEALTH_INDICATORS);
      fixture.detectChanges();

      expect(region(view)).toBeNull();
      expect(view.querySelector('h1')?.textContent).toContain('Indicadores de Saúde');
    });
  });

  describe('listagem de orçamentos', () => {
    function mount() {
      const responses: Subject<Page<Quote>>[] = [];
      const requests: PageRequest[] = [];
      TestBed.configureTestingModule({
        providers: [
          provideRouter([]),
          provideHttpClient(),
          provideHttpClientTesting(),
          {
            provide: QuotesService,
            useValue: {
              list: (request: PageRequest) => {
                requests.push(request);
                responses.push(new Subject<Page<Quote>>());
                return responses[responses.length - 1];
              },
            },
          },
          { provide: LoginService, useValue: { currentUser: () => 'Cliente Lactec' } },
          { provide: AppDialogService, useValue: { open: () => ({ afterClosed: () => NEVER }) } },
        ],
      });
      const fixture = TestBed.createComponent(QuoteListComponent);
      fixture.detectChanges();
      return { fixture, responses, requests, view: fixture.nativeElement as HTMLElement };
    }

    it('mostra cartões de skeleton na primeira carga, e não "nenhum orçamento"', () => {
      const { view } = mount();

      expectSilentBusyRegion(view);
      expect(view.querySelectorAll('app-quote-card-skeleton').length).toBe(3);
      expect(view.textContent).not.toContain('Nenhum orçamento encontrado');
    });

    it('troca os skeletons pelos cartões quando a página chega', () => {
      const { fixture, responses, requests, view } = mount();

      responses[0].next(pageOf([QUOTE], requests[0]));
      fixture.detectChanges();

      expect(region(view)).toBeNull();
      expect(view.querySelectorAll('article.quote-card').length).toBe(1);
    });

    it('ao trocar de página mantém os cartões atuais esmaecidos, sem skeleton', () => {
      const { fixture, responses, requests, view } = mount();

      responses[0].next(pageOf([QUOTE], requests[0], 40));
      fixture.detectChanges();
      fixture.componentInstance.goToPage(2);
      fixture.detectChanges();

      const list = view.querySelector('article.quote-card')!.parentElement!;

      expect(region(view)).toBeNull();
      expect(list.classList).toContain('opacity-60');
      expect(list.getAttribute('aria-busy')).toBe('true');

      responses[1].next(pageOf([QUOTE], requests[1], 40));
      fixture.detectChanges();

      expect(list.classList).not.toContain('opacity-60');
      expect(list.hasAttribute('aria-busy')).toBeFalse();
    });
  });

  describe('detalhe do equipamento', () => {
    it('desenha cabeçalho, os dez cartões de placa e a tabela do histórico', () => {
      TestBed.configureTestingModule({
        providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
      });
      const fixture = TestBed.createComponent(EquipmentDetailComponent);
      fixture.componentRef.setInput('id', 'NTcxMjg5');
      fixture.detectChanges();
      const view: HTMLElement = fixture.nativeElement;

      expectSilentBusyRegion(view);
      expect(view.querySelector('app-page-header-skeleton')).not.toBeNull();
      expect(view.querySelectorAll('app-loading-region .grid > div').length).toBe(10);
      expect(view.querySelector('app-table-skeleton')).not.toBeNull();
      expect(view.querySelector('app-data-table')).toBeNull();

      TestBed.inject(HttpTestingController)
        .expectOne(`${API_URL}/api/equipments/NTcxMjg5`)
        .flush(null, { status: 500, statusText: 'erro' });
      fixture.detectChanges();

      expect(region(view)).toBeNull();
      expect(view.querySelector('[role="alert"]')).not.toBeNull();
    });
  });

  describe('detalhe do orçamento', () => {
    function mount() {
      const quote = new Subject<Quote>();
      const document = new Subject<Blob>();
      TestBed.configureTestingModule({
        providers: [
          provideRouter([]),
          provideHttpClient(),
          provideHttpClientTesting(),
          {
            provide: QuotesService,
            useValue: { getQuoteById: () => quote, quoteDocument: () => document },
          },
          { provide: LoginService, useValue: { currentUser: () => 'Cliente Lactec' } },
          { provide: MatSnackBar, useValue: { open: () => {} } },
          { provide: AppDialogService, useValue: { open: () => ({ afterClosed: () => NEVER }) } },
        ],
      });
      usePdfViewerStub();
      const fixture = TestBed.createComponent(QuoteDetailComponent);
      fixture.componentRef.setInput('id', 'quote-15');
      fixture.detectChanges();
      return { fixture, quote, document, view: fixture.nativeElement as HTMLElement };
    }

    it('desenha a página inteira, com a moldura da proposta, enquanto o orçamento não chega', () => {
      const { view } = mount();

      expectSilentBusyRegion(view);
      expect(view.querySelector('app-page-header-skeleton')).not.toBeNull();
      expect(view.querySelector('app-proposal-skeleton')).not.toBeNull();
    });

    it('com o orçamento na tela, o visualizador guarda a altura da proposta até o PDF chegar', () => {
      const { fixture, quote, document, view } = mount();

      quote.next({ ...QUOTE, stage: 'Negociação' });
      fixture.detectChanges();
      fixture.detectChanges();

      const viewer = view.querySelector('app-proposal-viewer')!;
      const page = viewer.querySelector<HTMLElement>('app-proposal-skeleton > div > div')!;

      expect(view.querySelector('h2')?.textContent).toContain(QUOTE.name);
      expect(viewer.querySelector('app-loading-region')?.getAttribute('aria-busy')).toBe('true');
      expect(page.style.height).toBe('70vh');
      expect(viewer.textContent).not.toContain('Carregando');

      document.next(new Blob(['%PDF-1.4'], { type: 'application/pdf' }));
      fixture.detectChanges();

      expect(viewer.querySelector('app-loading-region')).toBeNull();
      expect(viewer.querySelector('ngx-extended-pdf-viewer')).not.toBeNull();
    });
  });

  describe('relatórios do orçamento', () => {
    function mount() {
      const quote = new Subject<Quote>();
      TestBed.configureTestingModule({
        providers: [
          provideRouter([]),
          provideHttpClient(),
          provideHttpClientTesting(),
          { provide: QuotesService, useValue: { getQuoteById: () => quote } },
          { provide: ReportsService, useValue: { list: () => NEVER } },
        ],
      });
      const fixture = TestBed.createComponent(QuoteReportsComponent);
      fixture.componentRef.setInput('quoteId', QUOTE.id);
      fixture.detectChanges();
      return { fixture, quote, view: fixture.nativeElement as HTMLElement };
    }

    it('desenha cabeçalho, etapas, resumo, busca e tabela enquanto o orçamento não chega', () => {
      const { view } = mount();

      expectSilentBusyRegion(view);
      expect(view.querySelectorAll('.status-stepper__step').length).toBe(5);
      expect(view.querySelector('app-table-skeleton')).not.toBeNull();
    });

    it('com o orçamento na tela, resumo e tabela seguem em skeleton até os laudos chegarem', fakeAsync(() => {
      const { fixture, quote, view } = mount();

      quote.next(QUOTE);
      renderMeasuredTable(fixture);

      expect(region(view)).toBeNull();
      expect(view.querySelector('h1')?.textContent).toContain('Conclusão da Análise');
      expect(view.textContent).not.toContain('Carregando');
      expect(view.querySelector('section p app-skeleton')?.getAttribute('aria-hidden')).toBe('true');
      expect(view.querySelector('tbody[data-skeleton]')).not.toBeNull();
    }));
  });

  describe('relatórios, aba de orçamentos', () => {
    function mount() {
      const responses: Subject<Page<Quote>>[] = [];
      const requests: PageRequest[] = [];
      TestBed.configureTestingModule({
        providers: [
          provideRouter([]),
          provideHttpClient(),
          provideHttpClientTesting(),
          {
            provide: QuotesService,
            useValue: {
              list: (request: PageRequest) => {
                requests.push(request);
                responses.push(new Subject<Page<Quote>>());
                return responses[responses.length - 1];
              },
            },
          },
          {
            provide: ReportsService,
            useValue: {
              list: (request: PageRequest) =>
                of({ items: [], total: 0, page: request.page, pageSize: request.pageSize }),
            },
          },
        ],
      });
      const fixture = TestBed.createComponent(ReportListComponent);
      fixture.detectChanges();
      const view: HTMLElement = fixture.nativeElement;
      view.querySelector<HTMLButtonElement>('#tab-quotes')!.click();
      fixture.detectChanges();
      return { fixture, responses, requests, view };
    }

    it('mostra cartões de skeleton até a primeira página de orçamentos chegar', () => {
      const { fixture, responses, requests, view } = mount();
      const panel = view.querySelector<HTMLElement>('#panel-quotes')!;

      expect(panel.querySelector('app-loading-region')?.getAttribute('aria-busy')).toBe('true');
      expect(panel.querySelectorAll('app-quote-card-skeleton').length).toBe(3);
      expect(panel.textContent).not.toContain('Carregando');

      responses[0].next(pageOf([QUOTE], requests[0]));
      fixture.detectChanges();

      expect(panel.querySelector('app-loading-region')).toBeNull();
      expect(panel.querySelectorAll('article.quote-card').length).toBe(1);
    });
  });
});
