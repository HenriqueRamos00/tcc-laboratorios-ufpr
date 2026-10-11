import { HttpErrorResponse } from '@angular/common/http';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter } from '@angular/router';
import { finalize, of, Subject, throwError } from 'rxjs';

import { Equipamento } from '@/app/model/equipamento';
import { EquipamentosService } from '@/app/services/equipamentos.service';
import { ConfirmacaoDialogComponent } from '@shared/components/confirmacao-dialog/confirmacao-dialog.component';
import { EquipamentoFormDialogComponent } from './equipamento-form-dialog.component';
import { EquipamentosComponent } from './equipamentos.component';

describe('Página de equipamentos', () => {
  let service: jasmine.SpyObj<EquipamentosService>;
  let abrirDialog: jasmine.Spy;
  let snackOpen: jasmine.Spy;

  const trafo: Equipamento = {
    id: 12,
    serialNumber: '000123',
    tag: 'TR-B05',
    substation: 'Subestação Norte',
    primaryVoltage: '138',
    owner: 'Copel',
    type: 'TRANSFORMADOR',
    riskLevel: 'ATENCAO',
  };
  const disjuntor: Equipamento = {
    id: 13,
    serialNumber: 'D-77',
    tag: 'DJ-C01',
    substation: 'Subestação Sul',
    primaryVoltage: '69',
    owner: 'Lactec',
    type: 'DISJUNTOR',
    riskLevel: 'CRITICO',
  };

  beforeEach(() => {
    service = jasmine.createSpyObj<EquipamentosService>('EquipamentosService', [
      'list', 'create', 'update', 'deactivate',
    ]);
    service.list.and.returnValue(of([]));
    service.deactivate.and.returnValue(of(void 0));
    abrirDialog = jasmine.createSpy('open').and.returnValue({ afterClosed: () => of(undefined) });
    snackOpen = jasmine.createSpy('open');
    TestBed.configureTestingModule({
      imports: [EquipamentosComponent],
      providers: [
        provideRouter([]),
        { provide: EquipamentosService, useValue: service },
      ],
    });
    TestBed.overrideProvider(MatDialog, { useValue: { open: abrirDialog } });
    TestBed.overrideProvider(MatSnackBar, { useValue: { open: snackOpen } });
  });

  function createPage() {
    const fixture = TestBed.createComponent(EquipamentosComponent);
    fixture.detectChanges();
    return fixture;
  }

  function loadPage(fixture: ReturnType<typeof createPage>): void {
    tick(300);
    fixture.detectChanges();
  }

  it('mostra o estado de carregamento até a lista responder', fakeAsync(() => {
    const resposta = new Subject<Equipamento[]>();
    service.list.and.returnValue(resposta);
    const fixture = createPage();

    expect(fixture.nativeElement.textContent).toContain('Carregando equipamentos');
    tick(300);
    fixture.detectChanges();
    expect(service.list).toHaveBeenCalledOnceWith('', null, null);
    expect(fixture.nativeElement.textContent).toContain('Carregando equipamentos');

    resposta.next([trafo]);
    resposta.complete();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('TR-B05');
    expect(fixture.nativeElement.textContent).not.toContain('Carregando equipamentos');
    fixture.destroy();
  }));

  it('mostra estado vazio após uma resposta bem-sucedida sem equipamentos', fakeAsync(() => {
    const fixture = createPage();
    loadPage(fixture);

    expect(fixture.nativeElement.textContent).toContain('Nenhum equipamento encontrado');
    expect(fixture.nativeElement.textContent).not.toContain('Não foi possível carregar');
    fixture.destroy();
  }));

  it('exibe erro e tenta carregar novamente', fakeAsync(() => {
    service.list.and.returnValues(
      throwError(() => new HttpErrorResponse({ status: 500 })),
      of([trafo]),
    );
    const fixture = createPage();
    loadPage(fixture);

    expect(fixture.nativeElement.textContent).toContain('Não foi possível carregar os equipamentos');
    [...fixture.nativeElement.querySelectorAll('button')]
      .find((button) => button.textContent?.includes('Tentar novamente'))?.click();
    fixture.detectChanges();

    expect(service.list).toHaveBeenCalledTimes(2);
    expect(fixture.nativeElement.textContent).toContain('TR-B05');
    fixture.destroy();
  }));

  it('aguarda 300 ms para buscar e combina texto, tipo e risco', fakeAsync(() => {
    const fixture = createPage();
    loadPage(fixture);
    fixture.componentInstance.typeControl.setValue('TRANSFORMADOR');
    fixture.componentInstance.riskControl.setValue('ATENCAO');
    service.list.calls.reset();
    fixture.componentInstance.searchControl.setValue('  TR-B05 ');
    expect(service.list).not.toHaveBeenCalled();
    tick(299);
    expect(service.list).not.toHaveBeenCalled();
    tick(1);

    expect(service.list).toHaveBeenCalledOnceWith('  TR-B05 ', 'TRANSFORMADOR', 'ATENCAO');
    fixture.destroy();
  }));

  for (const cleared of ['search', 'type', 'risk'] as const) {
    it(`preserva os outros filtros ao limpar ${cleared}`, fakeAsync(() => {
      const fixture = createPage();
      loadPage(fixture);
      fixture.componentInstance.searchControl.setValue('TR-B05');
      fixture.componentInstance.typeControl.setValue('TRANSFORMADOR');
      fixture.componentInstance.riskControl.setValue('ATENCAO');
      tick(300);
      service.list.calls.reset();

      if (cleared === 'search') fixture.componentInstance.searchControl.setValue('');
      if (cleared === 'type') fixture.componentInstance.typeControl.setValue('');
      if (cleared === 'risk') fixture.componentInstance.riskControl.setValue('');
      tick(300);

      expect(service.list).toHaveBeenCalledTimes(1);
      expect(service.list).toHaveBeenCalledWith(
        cleared === 'search' ? '' : 'TR-B05',
        cleared === 'type' ? null : 'TRANSFORMADOR',
        cleared === 'risk' ? null : 'ATENCAO',
      );
      fixture.destroy();
    }));
  }

  it('cancela a requisição anterior quando uma busca mais nova chega', fakeAsync(() => {
    const respostas: Subject<Equipamento[]>[] = [];
    const cancelada = jasmine.createSpy('cancelada');
    service.list.and.callFake(() => {
      const resposta = new Subject<Equipamento[]>();
      respostas.push(resposta);
      return resposta.pipe(finalize(() => cancelada()));
    });
    const fixture = createPage();
    tick(300);
    fixture.componentInstance.searchControl.setValue('antiga');
    tick(300);
    fixture.componentInstance.searchControl.setValue('nova');
    tick(300);

    expect(cancelada).toHaveBeenCalledTimes(2);
    expect(service.list).toHaveBeenCalledTimes(3);
    respostas[1].next([trafo]);
    fixture.detectChanges();
    expect(fixture.componentInstance.equipamentos()).toEqual([]);
    respostas[2].next([disjuntor]);
    fixture.detectChanges();
    expect(fixture.componentInstance.equipamentos()).toEqual([disjuntor]);
    fixture.destroy();
  }));

  it('abre cadastro sem equipamento e não atualiza a lista ao cancelar', fakeAsync(() => {
    const fechado = new Subject<Equipamento | undefined>();
    abrirDialog.and.returnValue({ afterClosed: () => fechado });
    const fixture = createPage();
    loadPage(fixture);
    fixture.componentInstance.novoEquipamento();
    expect(abrirDialog).toHaveBeenCalledWith(
      EquipamentoFormDialogComponent,
      jasmine.objectContaining({
        data: { equipamento: null },
        ariaLabel: 'Cadastrar novo equipamento',
      }),
    );
    fechado.next(undefined);

    expect(service.list).toHaveBeenCalledTimes(1);
    expect(service.create).not.toHaveBeenCalled();
    expect(service.update).not.toHaveBeenCalled();
    expect(service.deactivate).not.toHaveBeenCalled();
    fixture.destroy();
  }));

  it('abre edição com a linha selecionada e não atualiza a lista ao cancelar', fakeAsync(() => {
    const fechado = new Subject<Equipamento | undefined>();
    abrirDialog.and.returnValue({ afterClosed: () => fechado });
    const fixture = createPage();
    service.list.and.returnValue(of([trafo]));
    loadPage(fixture);
    fixture.componentInstance.editarEquipamento(trafo);
    expect(abrirDialog).toHaveBeenCalledWith(
      EquipamentoFormDialogComponent,
      jasmine.objectContaining({
        data: { equipamento: trafo },
        ariaLabel: 'Editar equipamento TR-B05',
      }),
    );
    fechado.next(undefined);

    expect(service.list).toHaveBeenCalledTimes(1);
    expect(service.create).not.toHaveBeenCalled();
    expect(service.update).not.toHaveBeenCalled();
    fixture.destroy();
  }));

  for (const edicao of [false, true]) {
    it(`atualiza a lista após ${edicao ? 'editar' : 'cadastrar'} e mantém os filtros`, fakeAsync(() => {
      const fechado = new Subject<Equipamento | undefined>();
      abrirDialog.and.returnValue({ afterClosed: () => fechado });
      const fixture = createPage();
      loadPage(fixture);
      fixture.componentInstance.searchControl.setValue('TR-B05');
      fixture.componentInstance.typeControl.setValue('TRANSFORMADOR');
      fixture.componentInstance.riskControl.setValue('ATENCAO');
      tick(300);
      service.list.calls.reset();

      if (edicao) fixture.componentInstance.editarEquipamento(trafo);
      else fixture.componentInstance.novoEquipamento();
      fechado.next(trafo);

      expect(service.list).toHaveBeenCalledOnceWith('TR-B05', 'TRANSFORMADOR', 'ATENCAO');
      expect(snackOpen).toHaveBeenCalledWith(
        edicao ? 'Equipamento atualizado.' : 'Equipamento cadastrado.', 'Fechar', jasmine.any(Object),
      );
      fixture.destroy();
    }));
  }

  it('só desativa após confirmação e atualiza a lista que já não contém o equipamento', fakeAsync(() => {
    const confirmacao = new Subject<boolean | undefined>();
    abrirDialog.and.returnValue({ afterClosed: () => confirmacao });
    service.list.and.returnValues(of([trafo]), of([]));
    const fixture = createPage();
    loadPage(fixture);
    fixture.componentInstance.desativarEquipamento(trafo);

    expect(abrirDialog).toHaveBeenCalledWith(ConfirmacaoDialogComponent, jasmine.objectContaining({
      data: jasmine.objectContaining({
        mensagem: jasmine.stringContaining(trafo.tag),
        perigo: true,
      }),
    }));
    expect(service.deactivate).not.toHaveBeenCalled();
    confirmacao.next(true);

    expect(service.deactivate).toHaveBeenCalledOnceWith(trafo.id);
    expect(service.list).toHaveBeenCalledTimes(2);
    expect(fixture.componentInstance.equipamentos()).toEqual([]);
    expect(snackOpen).toHaveBeenCalledWith('Equipamento desativado.', 'Fechar', jasmine.any(Object));
    fixture.destroy();
  }));

  for (const resultado of [false, undefined]) {
    it(`não desativa nem atualiza ao ${resultado === false ? 'cancelar' : 'fechar sem confirmar'}`, fakeAsync(() => {
      abrirDialog.and.returnValue({ afterClosed: () => of(resultado) });
      service.list.and.returnValue(of([trafo]));
      const fixture = createPage();
      loadPage(fixture);
      fixture.componentInstance.desativarEquipamento(trafo);

      expect(service.deactivate).not.toHaveBeenCalled();
      expect(service.list).toHaveBeenCalledTimes(1);
      expect(fixture.componentInstance.equipamentos()).toEqual([trafo]);
      fixture.destroy();
    }));
  }

  it('mantém a linha e informa falha ao desativar', fakeAsync(() => {
    abrirDialog.and.returnValue({ afterClosed: () => of(true) });
    service.list.and.returnValue(of([trafo]));
    service.deactivate.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    const fixture = createPage();
    loadPage(fixture);
    fixture.componentInstance.desativarEquipamento(trafo);

    expect(service.deactivate).toHaveBeenCalledOnceWith(trafo.id);
    expect(service.list).toHaveBeenCalledTimes(1);
    expect(fixture.componentInstance.equipamentos()).toEqual([trafo]);
    expect(snackOpen).toHaveBeenCalledWith(
      'Não foi possível desativar o equipamento. Tente novamente.', 'Fechar', jasmine.any(Object),
    );
    fixture.destroy();
  }));
});
