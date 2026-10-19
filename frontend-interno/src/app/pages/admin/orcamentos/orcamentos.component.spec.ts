import { HttpErrorResponse } from '@angular/common/http';
import { DATE_PIPE_DEFAULT_OPTIONS, registerLocaleData } from '@angular/common';
import pt from '@angular/common/locales/pt';
import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter } from '@angular/router';
import { Observable, of, Subject, throwError } from 'rxjs';

import { routes } from '@/app/app.routes';
import { Orcamento, OrcamentoDetalhe, PaginaOrcamentos } from '@/app/model/orcamento';
import { AppDialogService } from '@/app/services/app-dialog.service';
import { OrcamentosService } from '@/app/services/orcamentos.service';
import { QuoteDetailDialogComponent } from './quote-detail-dialog.component';
import { QuoteFormDialogComponent, QuoteFormResult } from './quote-form-dialog.component';
import { OrcamentosComponent } from './orcamentos.component';

registerLocaleData(pt);

describe('Página de orçamentos', () => {
  let service: jasmine.SpyObj<OrcamentosService>;
  let dialog: jasmine.SpyObj<AppDialogService>;

  const orcamento: Orcamento = {
    id: 'quote-1',
    code: 'EAQ_2026_45482_V_1',
    name: 'Análise de óleo isolante',
    status: 'PendingAcceptance',
    stage: 'PendingAcceptance',
    description: 'Análise físico-química de óleo isolante',
    companyName: 'Copel Distribuição',
    externalContactName: 'Ana Lima',
    externalContactEmail: 'ana@example.com',
    totalPrice: 12500,
    createdDate: '2026-10-15T10:00:00Z',
    hasDocument: true,
  };

  const detalhe: OrcamentoDetalhe = {
    ...orcamento,
    companyId: 'company-1',
    laboratoryName: 'Laboratório de Ensaios',
    scope: null,
    items: [],
  };

  function pagina(items: readonly Orcamento[], page = 1, totalItems = items.length): PaginaOrcamentos {
    return { items, page, pageSize: 20, totalItems };
  }

  beforeEach(() => {
    service = jasmine.createSpyObj<OrcamentosService>(
      'OrcamentosService', ['list', 'get', 'downloadDocument'],
    );
    dialog = jasmine.createSpyObj<AppDialogService>('AppDialogService', ['open', 'openLateral']);
    dialog.open.and.returnValue({ afterClosed: () => of(undefined) } as never);

    TestBed.configureTestingModule({
      imports: [OrcamentosComponent],
      providers: [
        provideRouter([]),
        { provide: DATE_PIPE_DEFAULT_OPTIONS, useValue: { timezone: '-0300' } },
        { provide: OrcamentosService, useValue: service },
        { provide: AppDialogService, useValue: dialog },
      ],
    });
  });

  it('carrega a página inicial e abre os detalhes de leitura', fakeAsync(() => {
    service.list.and.returnValue(of(pagina([orcamento], 1, 21)));
    const fixture = TestBed.createComponent(OrcamentosComponent);
    fixture.detectChanges();
    tick();
    fixture.detectChanges();

    expect(service.list).toHaveBeenCalledOnceWith({ search: '', status: null, page: 1, pageSize: 20 });
    expect(fixture.nativeElement.textContent).toContain('Copel Distribuição');
    expect(fixture.nativeElement.textContent).toContain('Análise físico-química de óleo isolante');
    expect(fixture.nativeElement.querySelector('[aria-label^="Abrir detalhes do orçamento"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelectorAll('[aria-label^="Editar orçamento"]').length).toBe(1);
    expect(fixture.nativeElement.querySelectorAll('[aria-label^="Baixar PDF do orçamento"]').length).toBe(1);
    expect(fixture.nativeElement.querySelector('[aria-label^="Visualizar orçamento"]')).toBeNull();

    fixture.componentInstance.visualizar(orcamento);
    expect(dialog.openLateral).toHaveBeenCalledWith(
      QuoteDetailDialogComponent,
      { quoteId: 'quote-1', quoteCode: orcamento.code },
      `Detalhes do orçamento ${orcamento.code}`,
    );
    fixture.destroy();
  }));

  it('exibe a data local da API na tabela perto da meia-noite UTC', fakeAsync(() => {
    const criadoPertoDaMeiaNoite = {
      ...orcamento,
      createdDate: '2026-10-18T02:30:00Z',
    };
    service.list.and.returnValue(of(pagina([criadoPertoDaMeiaNoite])));
    const fixture = TestBed.createComponent(OrcamentosComponent);
    fixture.detectChanges();
    tick();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('17/10/2026');
    expect(fixture.nativeElement.textContent).not.toContain('18/10/2026');
    fixture.destroy();
  }));

  it('cancela a página em andamento ao mudar a busca e pagina no servidor', fakeAsync(() => {
    let cancelada = false;
    const paginaSeguinte$ = new Observable<PaginaOrcamentos>(() => () => cancelada = true);
    service.list.and.returnValues(
      of(pagina([orcamento], 1, 25)),
      paginaSeguinte$,
      of(pagina([], 1, 0)),
    );
    const fixture = TestBed.createComponent(OrcamentosComponent);
    fixture.detectChanges();
    tick();

    fixture.componentInstance.onPageChange({ pageIndex: 1, pageSize: 20, length: 25, previousPageIndex: 0 });
    expect(service.list.calls.mostRecent().args[0].page).toBe(2);
    expect(fixture.componentInstance.carregando()).toBeTrue();

    fixture.componentInstance.searchControl.setValue('Copel');
    expect(cancelada).toBeTrue();
    expect(fixture.componentInstance.carregando()).toBeTrue();
    tick(299);
    expect(service.list).toHaveBeenCalledTimes(2);
    tick(1);
    expect(service.list.calls.mostRecent().args[0]).toEqual({
      search: 'Copel', status: null, page: 1, pageSize: 20,
    });
    expect(fixture.componentInstance.carregando()).toBeFalse();
    fixture.destroy();
  }));

  it('volta para a primeira página ao trocar o status e omite status para Todos', fakeAsync(() => {
    service.list.and.returnValues(
      of(pagina([orcamento], 1, 25)),
      of(pagina([orcamento], 2, 25)),
      of(pagina([orcamento], 1, 12)),
      of(pagina([orcamento], 1, 25)),
    );
    const fixture = TestBed.createComponent(OrcamentosComponent);
    fixture.detectChanges();
    tick();

    fixture.componentInstance.onPageChange({ pageIndex: 1, pageSize: 20, length: 25, previousPageIndex: 0 });
    expect(service.list.calls.mostRecent().args[0].page).toBe(2);
    fixture.componentInstance.statusControl.setValue('InProgress');
    tick(300);
    expect(service.list.calls.mostRecent().args[0]).toEqual({
      search: '', status: 'InProgress', page: 1, pageSize: 20,
    });

    fixture.componentInstance.statusControl.setValue('ALL');
    tick(300);
    expect(service.list.calls.mostRecent().args[0]).toEqual({
      search: '', status: null, page: 1, pageSize: 20,
    });
    fixture.destroy();
  }));

  it('apresenta os status comerciais locais e trata rótulos inesperados com segurança', fakeAsync(() => {
    service.list.and.returnValue(of(pagina([orcamento])));
    const fixture = TestBed.createComponent(OrcamentosComponent);
    fixture.detectChanges();
    tick();
    fixture.detectChanges();

    const seletor = fixture.nativeElement.querySelector('#orcamentos-status-filter') as HTMLSelectElement;
    const opcoesStatus = seletor.querySelectorAll<HTMLOptionElement>('option');
    const opcoes = Array.from(opcoesStatus, (option) => option.textContent?.trim());
    expect(opcoes).toEqual(['Todos os status', 'Pendente de Aceite', 'Em Andamento', 'Finalizado', 'Recusado', 'Cancelado']);
    expect(fixture.componentInstance.rotuloStatus('PendingAcceptance')).toBe('Pendente de Aceite');
    expect(fixture.componentInstance.rotuloStatus('constructor')).toBe('constructor');
    expect(fixture.componentInstance.rotuloStatus('toString')).toBe('toString');
    expect(fixture.componentInstance.classeStatus('constructor')).toContain('bg-lactec-line-soft');
    expect(fixture.componentInstance.classeStatus('toString')).toContain('bg-lactec-line-soft');

    fixture.destroy();
  }));

  it('mantém a paginação acessível quando a página atual fica vazia', fakeAsync(() => {
    service.list.and.returnValue(of(pagina([], 2, 21)));
    const fixture = TestBed.createComponent(OrcamentosComponent);
    fixture.detectChanges();
    tick();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Nenhum orçamento encontrado');
    expect(fixture.nativeElement.querySelector('mat-paginator')).not.toBeNull();
    fixture.destroy();
  }));

  it('mostra literalmente um status desconhecido com estilo neutro', fakeAsync(() => {
    const inesperado = { ...orcamento, status: 'OtherStatus', hasDocument: false };
    service.list.and.returnValue(of(pagina([inesperado])));
    const fixture = TestBed.createComponent(OrcamentosComponent);
    fixture.detectChanges();
    tick();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('OtherStatus');
    expect(fixture.nativeElement.querySelector('[aria-label^="Baixar PDF do orçamento"]')).toBeNull();
    expect(fixture.componentInstance.rotuloStatus('OtherStatus')).toBe('OtherStatus');
    expect(fixture.componentInstance.classeStatus('OtherStatus')).toContain('bg-lactec-line-soft');
    fixture.destroy();
  }));

  it('separa falha no download do estado da listagem', fakeAsync(() => {
    service.list.and.returnValue(of(pagina([orcamento])));
    service.downloadDocument.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 404, statusText: 'Not Found' })),
    );
    const fixture = TestBed.createComponent(OrcamentosComponent);
    const snackBar = fixture.debugElement.injector.get(MatSnackBar);
    spyOn(snackBar, 'open');
    fixture.detectChanges();
    tick();

    fixture.componentInstance.baixar(orcamento);
    expect(service.downloadDocument).toHaveBeenCalledOnceWith('quote-1');
    expect(fixture.componentInstance.erro()).toBeFalse();
    expect(fixture.componentInstance.orcamentos()).toEqual([orcamento]);
    expect(snackBar.open).toHaveBeenCalledWith(
      'O PDF deste orçamento não está mais disponível.', 'Fechar', jasmine.objectContaining({ duration: 5000 }),
    );
    fixture.destroy();
  }));

  it('apresenta estados distintos de erro, vazio e carregamento', fakeAsync(() => {
    service.list.and.returnValues(
      throwError(() => new HttpErrorResponse({ status: 403, statusText: 'Forbidden' })),
      of(pagina([])),
    );
    const fixture = TestBed.createComponent(OrcamentosComponent);
    fixture.detectChanges();
    tick();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Acesso não autorizado');

    fixture.componentInstance.tentarNovamente();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Nenhum orçamento encontrado');
    fixture.destroy();
  }));

  it('mantém a pesquisa de orçamentos na rota ADMIN protegida', () => {
    const rotaAdmin = routes.find((route) => route.path === 'admin');
    const rotaOrcamentos = rotaAdmin?.children?.find((route) => route.path === 'orcamentos');

    expect(rotaAdmin?.data?.['role']).toBe('ADMIN');
    expect(rotaOrcamentos?.loadComponent).toBeDefined();
    expect(rotaOrcamentos?.title).toBe('Orçamentos - Portal Interno Lactec');
  });

  it('abre criação e edição e atualiza a página preservando os filtros', fakeAsync(() => {
    service.list.and.callFake((pesquisa) => of(pagina([orcamento], pesquisa.page, 25)));
    const criarResultado = new Subject<QuoteFormResult | undefined>();
    const editarResultado = new Subject<QuoteFormResult | undefined>();
    dialog.openLateral.and.returnValues(
      { afterClosed: () => criarResultado } as never,
      { afterClosed: () => editarResultado } as never,
    );
    const fixture = TestBed.createComponent(OrcamentosComponent);
    const snackBar = fixture.debugElement.injector.get(MatSnackBar);
    spyOn(snackBar, 'open');
    fixture.detectChanges();
    tick();

    fixture.componentInstance.onPageChange({ pageIndex: 1, pageSize: 20, length: 25, previousPageIndex: 0 });
    expect(service.list.calls.mostRecent().args[0].page).toBe(2);
    fixture.componentInstance.novo();
    expect(dialog.openLateral.calls.mostRecent().args).toEqual([
      QuoteFormDialogComponent,
      { quoteId: null, quoteCode: null },
      'Novo orçamento',
    ]);
    criarResultado.next({ created: true, quote: detalhe });
    expect(service.list.calls.mostRecent().args[0]).toEqual({
      search: '', status: null, page: 1, pageSize: 20,
    });

    fixture.componentInstance.searchControl.setValue('Copel');
    tick(300);
    fixture.componentInstance.statusControl.setValue('InProgress');
    tick(300);
    fixture.componentInstance.onPageChange({ pageIndex: 1, pageSize: 20, length: 25, previousPageIndex: 0 });
    expect(service.list.calls.mostRecent().args[0]).toEqual({
      search: 'Copel', status: 'InProgress', page: 2, pageSize: 20,
    });
    fixture.componentInstance.editar(orcamento);
    expect(dialog.openLateral.calls.mostRecent().args).toEqual([
      QuoteFormDialogComponent,
      { quoteId: 'quote-1', quoteCode: orcamento.code },
      `Editar orçamento ${orcamento.code}`,
    ]);
    editarResultado.next({ created: false, quote: detalhe });
    expect(service.list.calls.mostRecent().args[0]).toEqual({
      search: 'Copel', status: 'InProgress', page: 2, pageSize: 20,
    });
    expect(snackBar.open).toHaveBeenCalledTimes(2);
    fixture.destroy();
  }));
});
