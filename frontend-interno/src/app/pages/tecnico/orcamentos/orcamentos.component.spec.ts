import { HttpErrorResponse } from '@angular/common/http';
import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { provideRouter } from '@angular/router';
import { Observable, of, Subject, throwError } from 'rxjs';
import { finalize } from 'rxjs/operators';

import {
  OrcamentoTecnico,
  OrcamentoTecnicoDetalhe,
  PaginaOrcamentosTecnicos,
} from '@/app/model/orcamento-tecnico';
import { OrcamentosTecnicosService } from '@/app/services/orcamentos-tecnicos.service';
import { AppDialogService } from '@/app/services/app-dialog.service';
import { OrcamentoTecnicoDetalheDialogComponent } from './orcamento-tecnico-detalhe-dialog.component';
import { OrcamentosTecnicosComponent } from './orcamentos.component';

describe('Página de orçamentos técnicos', () => {
  let service: jasmine.SpyObj<OrcamentosTecnicosService>;
  let dialog: jasmine.SpyObj<AppDialogService>;

  const orcamento: OrcamentoTecnico = {
    id: 'quote-1',
    code: 'EAQ_2026_33322_V_3',
    name: 'Análise de óleo isolante',
    companyName: 'Copel Distribuição',
    createdDate: '2026-10-15T10:00:00-03:00',
    technicalStatus: 'validated',
  };

  function pagina(
    items: readonly OrcamentoTecnico[],
    page = 1,
    totalItems = items.length,
  ): PaginaOrcamentosTecnicos {
    return { items, page, pageSize: 20, totalItems };
  }

  beforeEach(() => {
    service = jasmine.createSpyObj<OrcamentosTecnicosService>('OrcamentosTecnicosService', ['list', 'get']);
    service.list.and.returnValue(of(pagina([])));
    dialog = jasmine.createSpyObj<AppDialogService>('AppDialogService', ['open', 'openLateral']);
    dialog.openLateral.and.returnValue({ afterClosed: () => of(undefined) } as never);

    TestBed.configureTestingModule({
      imports: [OrcamentosTecnicosComponent],
      providers: [
        provideRouter([]),
        { provide: OrcamentosTecnicosService, useValue: service },
        { provide: AppDialogService, useValue: dialog },
      ],
    });
  });

  function criarPagina() {
    const fixture = TestBed.createComponent(OrcamentosTecnicosComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('carrega a consulta inicial e exibe somente os campos da lista técnica', fakeAsync(() => {
    service.list.and.returnValue(of(pagina([orcamento])));
    const fixture = criarPagina();
    fixture.detectChanges();

    expect(service.list).toHaveBeenCalledOnceWith({
      search: '',
      status: null,
      from: null,
      to: null,
      page: 1,
      pageSize: 20,
    });
    expect(fixture.nativeElement.textContent).toContain('EAQ_2026_33322_V_3');
    expect(fixture.nativeElement.textContent).toContain('Copel Distribuição');
    expect(fixture.nativeElement.textContent).toContain('Validado');
    expect(fixture.nativeElement.textContent).not.toContain('Pendente de Aceite');
    expect(fixture.nativeElement.textContent).not.toContain('R$');
    expect(fixture.nativeElement.querySelector('[aria-label^="Abrir orçamento"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('[aria-label^="Editar orçamento"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[aria-label^="Baixar PDF"]')).toBeNull();
    fixture.destroy();
  }));

  it('abre o drawer técnico somente pela ação Abrir', fakeAsync(() => {
    service.list.and.returnValue(of(pagina([orcamento])));
    const fixture = criarPagina();
    const abrir = fixture.nativeElement.querySelector('[aria-label="Abrir orçamento EAQ_2026_33322_V_3"]') as HTMLButtonElement;
    abrir.click();

    expect(dialog.openLateral).toHaveBeenCalledWith(
      OrcamentoTecnicoDetalheDialogComponent,
      { quoteId: 'quote-1', quoteCode: orcamento.code },
      `Detalhes técnicos do orçamento ${orcamento.code}`,
    );
    expect(service.get).not.toHaveBeenCalled();
    fixture.destroy();
  }));

  it('aguarda 300 ms para pesquisar texto e cancela uma resposta anterior', fakeAsync(() => {
    let consultasCanceladas = 0;
    service.list.and.callFake(() => new Observable<PaginaOrcamentosTecnicos>(
      () => () => consultasCanceladas++,
    ));
    const fixture = criarPagina();
    service.list.calls.reset();

    fixture.componentInstance.searchControl.setValue('Copel');
    expect(service.list).not.toHaveBeenCalled();
    tick(299);
    expect(service.list).not.toHaveBeenCalled();
    tick(1);
    expect(service.list).toHaveBeenCalledOnceWith({
      search: 'Copel', status: null, from: null, to: null, page: 1, pageSize: 20,
    });

    fixture.componentInstance.searchControl.setValue('EAQ');
    tick(300);
    expect(service.list).toHaveBeenCalledTimes(2);
    expect(consultasCanceladas).toBe(2);
    fixture.destroy();
  }));

  it('aplica status e datas imediatamente e retorna à primeira página', fakeAsync(() => {
    service.list.and.returnValue(of(pagina([orcamento], 1, 25)));
    const fixture = criarPagina();
    fixture.componentInstance.onPageChange({
      pageIndex: 1,
      pageSize: 20,
      length: 25,
      previousPageIndex: 0,
    });
    expect(service.list.calls.mostRecent().args[0].page).toBe(2);

    fixture.componentInstance.statusControl.setValue('validated');
    expect(service.list.calls.mostRecent().args[0]).toEqual({
      search: '', status: 'validated', from: null, to: null, page: 1, pageSize: 20,
    });

    fixture.componentInstance.fromControl.setValue('2026-10-01');
    expect(service.list.calls.mostRecent().args[0]).toEqual({
      search: '', status: 'validated', from: '2026-10-01', to: null, page: 1, pageSize: 20,
    });

    fixture.componentInstance.toControl.setValue('2026-10-31');
    expect(service.list.calls.mostRecent().args[0]).toEqual({
      search: '', status: 'validated', from: '2026-10-01', to: '2026-10-31', page: 1, pageSize: 20,
    });
    fixture.destroy();
  }));

  for (const [from, to, motivo] of [
    ['2026-10-20', '2026-10-01', 'ordem'],
    ['2026-02-30', '', 'calendário'],
    ['0000-01-01', '', 'limite de ano'],
    ['2026-1-01', '', 'formato'],
  ] as const) {
    it(`não envia datas inválidas por ${motivo} e mantém os campos preenchidos`, fakeAsync(() => {
      const fixture = criarPagina();
      service.list.calls.reset();
      fixture.componentInstance.fromControl.setValue(from);
      if (motivo === 'ordem') service.list.calls.reset();
      fixture.componentInstance.toControl.setValue(to);
      fixture.detectChanges();

      expect(service.list).not.toHaveBeenCalled();
      expect(fixture.componentInstance.carregando()).toBeFalse();
      expect(fixture.componentInstance.fromControl.value).toBe(from);
      expect(fixture.componentInstance.toControl.value).toBe(to);
      expect(fixture.componentInstance.intervaloDatasInvalido()).toBeTrue();
      expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent)
        .toContain(motivo === 'ordem' ? 'anterior ou igual' : 'data válida');
      fixture.destroy();
    }));
  }

  it('exibe erro de consulta e permite tentar novamente', fakeAsync(() => {
    service.list.and.returnValues(
      throwError(() => new HttpErrorResponse({ status: 503 })),
      of(pagina([orcamento])),
    );
    const fixture = criarPagina();
    expect(fixture.nativeElement.textContent).toContain('Não foi possível carregar os orçamentos');

    const tentarNovamente = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    tentarNovamente.click();
    fixture.detectChanges();

    expect(service.list).toHaveBeenCalledTimes(2);
    expect(fixture.nativeElement.textContent).toContain(orcamento.code);
    fixture.destroy();
  }));

  it('mantém os filtros e a página ao fechar o painel de detalhes', fakeAsync(() => {
    service.list.and.returnValue(of(pagina([orcamento], 2, 25)));
    const fixture = criarPagina();
    fixture.componentInstance.searchControl.setValue('Copel');
    tick(300);
    fixture.componentInstance.statusControl.setValue('validated');
    fixture.componentInstance.fromControl.setValue('2026-10-01');
    fixture.componentInstance.toControl.setValue('2026-10-31');
    const quantidadeConsultasAntesDoDrawer = service.list.calls.count();

    fixture.componentInstance.abrirDetalhes(orcamento);

    expect(service.list.calls.count()).toBe(quantidadeConsultasAntesDoDrawer);
    expect(fixture.componentInstance.searchControl.value).toBe('Copel');
    expect(fixture.componentInstance.statusControl.value).toBe('validated');
    expect(fixture.componentInstance.fromControl.value).toBe('2026-10-01');
    expect(fixture.componentInstance.toControl.value).toBe('2026-10-31');
    expect(fixture.componentInstance.pageIndex()).toBe(1);
    fixture.destroy();
  }));
});

describe('Drawer técnico de orçamento', () => {
  let service: jasmine.SpyObj<OrcamentosTecnicosService>;

  const detalhe: OrcamentoTecnicoDetalhe = {
    id: 'quote-1',
    code: 'EAQ_2026_33322_V_3',
    name: 'Análise de óleo isolante',
    companyName: 'Copel Distribuição',
    createdDate: '2026-10-15T10:00:00-03:00',
    technicalStatus: 'complete',
    description: 'Caracterização físico-química de óleo isolante',
    laboratoryName: 'Laboratório de Materiais',
    scope: 'Determinação de teor de água',
    items: [{ testType: 'Teor de água', sampleCount: 3, standard: 'ASTM D1533' }],
  };

  beforeEach(() => {
    service = jasmine.createSpyObj<OrcamentosTecnicosService>('OrcamentosTecnicosService', ['list', 'get']);
    TestBed.configureTestingModule({
      imports: [OrcamentoTecnicoDetalheDialogComponent],
      providers: [
        { provide: OrcamentosTecnicosService, useValue: service },
        { provide: MAT_DIALOG_DATA, useValue: { quoteId: 'quote-1', quoteCode: detalhe.code } },
      ],
    });
  });

  it('consulta os detalhes técnicos e não apresenta campos comerciais', fakeAsync(() => {
    service.get.and.returnValue(of(detalhe));
    const fixture = TestBed.createComponent(OrcamentoTecnicoDetalheDialogComponent);
    fixture.detectChanges();
    fixture.detectChanges();

    const texto = fixture.nativeElement.textContent as string;
    expect(service.get).toHaveBeenCalledOnceWith('quote-1');
    expect(texto).toContain('Copel Distribuição');
    expect(texto).toContain('Laboratório de Materiais');
    expect(texto).toContain('ASTM D1533');
    expect(texto).toContain('Quantidade prevista');
    expect(texto).not.toContain('R$');
    expect(texto).not.toContain('Contato');
    expect(texto).not.toContain('PDF');
    expect(fixture.nativeElement.querySelector('button[mat-dialog-close]')).not.toBeNull();
    fixture.destroy();
  }));

  it('apresenta status técnico ausente como Não informado', fakeAsync(() => {
    service.get.and.returnValue(of({ ...detalhe, technicalStatus: null }));
    const fixture = TestBed.createComponent(OrcamentoTecnicoDetalheDialogComponent);
    fixture.detectChanges();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Não informado');
    expect(fixture.nativeElement.textContent).not.toContain('Incompleto');
    fixture.destroy();
  }));

  it('permite repetir uma consulta de detalhe que falhou', fakeAsync(() => {
    service.get.and.returnValues(
      throwError(() => new HttpErrorResponse({ status: 500 })),
      of(detalhe),
    );
    const fixture = TestBed.createComponent(OrcamentoTecnicoDetalheDialogComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Não foi possível carregar os detalhes técnicos');

    const tentarNovamente = fixture.nativeElement.querySelector('[role="alert"] button') as HTMLButtonElement;
    tentarNovamente.click();
    fixture.detectChanges();

    expect(service.get).toHaveBeenCalledTimes(2);
    expect(fixture.nativeElement.textContent).toContain('ASTM D1533');
    fixture.destroy();
  }));
});
