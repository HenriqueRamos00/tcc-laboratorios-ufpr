import { HttpErrorResponse } from '@angular/common/http';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';

import { Empresa } from '@/app/model/empresa';
import { AppDialogService } from '@/app/services/app-dialog.service';
import { EmpresasService } from '@/app/services/empresas.service';
import { ConfirmacaoDialogComponent } from '@/app/shared/components/confirmacao-dialog/confirmacao-dialog.component';
import { EmpresasComponent } from './empresas.component';

describe('Página de empresas', () => {
  let service: jasmine.SpyObj<EmpresasService>;
  let dialog: jasmine.SpyObj<AppDialogService>;

  const empresa: Empresa = {
    id: 'empresa-1', legalName: 'Empresa Local', cnpj: '04368898000106', address: 'Rua Um, 10', city: 'Curitiba', state: 'PR',
    contactName: 'Ana Lima', contactPhone: '(41) 3333-4444', contactEmail: 'ana@example.com', status: 'ACTIVE', origin: 'LOCAL',
  };

  beforeEach(() => {
    service = jasmine.createSpyObj<EmpresasService>('EmpresasService', ['list', 'changeStatus']);
    dialog = jasmine.createSpyObj<AppDialogService>('AppDialogService', ['open', 'openLateral']);
    dialog.open.and.returnValue({ afterClosed: () => of(undefined) } as never);
    dialog.openLateral.and.returnValue({ afterClosed: () => of(undefined) } as never);
    TestBed.configureTestingModule({
      imports: [EmpresasComponent],
      providers: [
        provideRouter([]),
        { provide: EmpresasService, useValue: service },
        { provide: AppDialogService, useValue: dialog },
        { provide: MatSnackBar, useValue: { open: jasmine.createSpy('open') } },
      ],
    });
  });

  it('envia busca e status juntos e mostra os resultados retornados', fakeAsync(() => {
    service.list.and.returnValue(of([empresa]));
    const fixture = TestBed.createComponent(EmpresasComponent);
    fixture.detectChanges();
    tick(300);
    fixture.componentInstance.searchControl.setValue('Energia');
    fixture.componentInstance.statusControl.setValue('ACTIVE');
    tick(300);
    fixture.detectChanges();

    expect(service.list).toHaveBeenCalledWith('Energia', 'ACTIVE');
    expect(fixture.nativeElement.textContent).toContain('Empresa Local');
    expect(fixture.nativeElement.textContent).toContain('04.368.898/0001-06');
    fixture.destroy();
  }));

  it('apresenta ausência de resultados distinta de falha da API', fakeAsync(() => {
    service.list.and.returnValue(of([]));
    const fixture = TestBed.createComponent(EmpresasComponent);
    fixture.detectChanges();
    tick(300);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Nenhuma empresa encontrada');
    expect(fixture.nativeElement.textContent).not.toContain('Não foi possível carregar');
    fixture.destroy();
  }));

  it('exibe erro e permite tentar novamente quando a API falha', fakeAsync(() => {
    service.list.and.returnValues(
      throwError(() => new HttpErrorResponse({ status: 500 })),
      of([empresa]),
    );
    const fixture = TestBed.createComponent(EmpresasComponent);
    fixture.detectChanges();
    tick(300);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Não foi possível carregar as empresas');

    fixture.componentInstance.tentarNovamente();
    fixture.detectChanges();
    expect(fixture.componentInstance.empresas()).toEqual([empresa]);
    fixture.destroy();
  }));

  it('altera o status somente depois da confirmação do administrador', fakeAsync(() => {
    const confirmacao = new Subject<boolean | undefined>();
    service.list.and.returnValue(of([empresa]));
    service.changeStatus.and.returnValue(of({ ...empresa, status: 'INACTIVE' }));
    dialog.open.and.returnValue({ afterClosed: () => confirmacao } as never);
    const fixture = TestBed.createComponent(EmpresasComponent);
    fixture.detectChanges();
    tick(300);

    fixture.componentInstance.alternarStatus(empresa);
    const argumentosDialogo = dialog.open.calls.mostRecent().args;
    expect(argumentosDialogo[0]).toBe(ConfirmacaoDialogComponent);
    expect(argumentosDialogo[1]).toEqual(jasmine.objectContaining({
      perigo: true, mensagem: jasmine.stringContaining(empresa.legalName),
    }));
    expect(service.changeStatus).not.toHaveBeenCalled();

    confirmacao.next(true);
    expect(service.changeStatus).toHaveBeenCalledOnceWith('empresa-1', 'INACTIVE');
    fixture.destroy();
  }));
});
