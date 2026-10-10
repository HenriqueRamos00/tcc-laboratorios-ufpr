import { HttpErrorResponse } from '@angular/common/http';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';

import { TecnicosService } from '@/app/services/tecnicos.service';
import { ConfirmacaoDialogComponent } from '@/app/shared/components/confirmacao-dialog/confirmacao-dialog.component';
import { TecnicosComponent } from './tecnicos.component';

describe('Página de técnicos', () => {
  let service: jasmine.SpyObj<TecnicosService>;
  let abrirDialog: jasmine.Spy;

  beforeEach(() => {
    service = jasmine.createSpyObj<TecnicosService>('TecnicosService', ['list', 'changeStatus']);
    abrirDialog = jasmine.createSpy('open').and.returnValue({ afterClosed: () => of(undefined) });
    TestBed.configureTestingModule({
      imports: [TecnicosComponent],
      providers: [
        provideRouter([]),
        { provide: TecnicosService, useValue: service },
        { provide: MatSnackBar, useValue: { open: jasmine.createSpy('open') } },
      ],
    });
    TestBed.overrideProvider(MatDialog, { useValue: { open: abrirDialog } });
  });

  it('exibe um estado vazio quando a resposta bem-sucedida não contém resultados', fakeAsync(() => {
    service.list.and.returnValue(of([]));
    const fixture = TestBed.createComponent(TecnicosComponent);
    fixture.detectChanges();
    tick(300);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Nenhum técnico encontrado');
    expect(fixture.nativeElement.textContent).not.toContain('Não foi possível carregar');
    fixture.destroy();
  }));

  it('exibe erro com opção de tentar novamente quando a API falha', fakeAsync(() => {
    service.list.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    const fixture = TestBed.createComponent(TecnicosComponent);
    fixture.detectChanges();
    tick(300);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Não foi possível carregar os técnicos');
    expect(fixture.nativeElement.textContent).not.toContain('Nenhum técnico encontrado');
    fixture.destroy();
  }));

  it('recarrega a lista após a confirmação de salvamento no painel', fakeAsync(() => {
    const novoTecnico = {
      id: 3, name: 'Maria Santos', email: 'maria@example.com', unit: 'DOLEO', specialty: 'Óleos', status: 'ACTIVE' as const,
    };
    service.list.and.returnValues(of([]), of([novoTecnico]));
    abrirDialog.and.returnValue({ afterClosed: () => of(novoTecnico) });
    const fixture = TestBed.createComponent(TecnicosComponent);
    fixture.detectChanges();
    tick(300);
    expect(service.list).toHaveBeenCalledTimes(1);

    fixture.componentInstance.novoTecnico();
    fixture.detectChanges();

    expect(service.list).toHaveBeenCalledTimes(2);
    expect(fixture.componentInstance.tecnicos()).toEqual([novoTecnico]);
    fixture.destroy();
  }));

  for (const caso of [
    { descricao: 'desativa', status: 'ACTIVE' as const, novoStatus: 'INACTIVE' as const, perigo: true },
    { descricao: 'ativa', status: 'INACTIVE' as const, novoStatus: 'ACTIVE' as const, perigo: false },
  ]) {
    it(`só ${caso.descricao} o técnico após a confirmação e atualiza a lista`, fakeAsync(() => {
      const tecnico = {
        id: 3, name: 'Maria Santos', email: 'maria@example.com', unit: 'DOLEO', specialty: 'Óleos', status: caso.status,
      };
      const confirmacao = new Subject<boolean | undefined>();
      service.list.and.returnValue(of([tecnico]));
      service.changeStatus.and.returnValue(of({ ...tecnico, status: caso.novoStatus }));
      abrirDialog.and.returnValue({ afterClosed: () => confirmacao });
      const fixture = TestBed.createComponent(TecnicosComponent);
      fixture.detectChanges();
      tick(300);

      fixture.componentInstance.alternarStatus(tecnico);

      expect(abrirDialog).toHaveBeenCalledWith(ConfirmacaoDialogComponent, jasmine.objectContaining({
        data: jasmine.objectContaining({
          mensagem: jasmine.stringContaining(tecnico.name),
          perigo: caso.perigo,
        }),
      }));
      expect(service.changeStatus).not.toHaveBeenCalled();

      confirmacao.next(true);

      expect(service.changeStatus).toHaveBeenCalledOnceWith(tecnico.id, caso.novoStatus);
      expect(service.list).toHaveBeenCalledTimes(2);
      fixture.destroy();
    }));
  }

  for (const resultado of [false, undefined]) {
    it(`preserva o status ao ${resultado === false ? 'cancelar' : 'fechar sem confirmar'} o diálogo`, fakeAsync(() => {
      const tecnico = {
        id: 3, name: 'Maria Santos', email: 'maria@example.com', unit: 'DOLEO', specialty: 'Óleos', status: 'ACTIVE' as const,
      };
      service.list.and.returnValue(of([tecnico]));
      abrirDialog.and.returnValue({ afterClosed: () => of(resultado) });
      const fixture = TestBed.createComponent(TecnicosComponent);
      fixture.detectChanges();
      tick(300);

      fixture.componentInstance.alternarStatus(tecnico);

      expect(service.changeStatus).not.toHaveBeenCalled();
      expect(service.list).toHaveBeenCalledTimes(1);
      fixture.destroy();
    }));
  }
});
