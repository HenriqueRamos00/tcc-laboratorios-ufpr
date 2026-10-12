import { HttpErrorResponse } from '@angular/common/http';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';

import { Cliente } from '@/app/model/cliente';
import { AppDialogService } from '@/app/services/app-dialog.service';
import { ClientesService } from '@/app/services/clientes.service';
import { ConfirmacaoDialogComponent } from '@/app/shared/components/confirmacao-dialog/confirmacao-dialog.component';
import { ClientesComponent } from './clientes.component';

describe('Página de clientes', () => {
  let service: jasmine.SpyObj<ClientesService>;
  let dialog: jasmine.SpyObj<AppDialogService>;

  const cliente: Cliente = {
    id: 'cliente-1', name: 'Ana Lima', email: 'ana@example.com', phone: '(41) 3333-4444', position: 'Compradora',
    status: 'ACTIVE', companies: [{ id: 'empresa-1', name: 'Empresa Local', status: 'ACTIVE' }],
  };

  beforeEach(() => {
    service = jasmine.createSpyObj<ClientesService>('ClientesService', ['list', 'changeStatus']);
    dialog = jasmine.createSpyObj<AppDialogService>('AppDialogService', ['open', 'openLateral']);
    dialog.open.and.returnValue({ afterClosed: () => of(undefined) } as never);
    dialog.openLateral.and.returnValue({ afterClosed: () => of(undefined) } as never);
    TestBed.configureTestingModule({
      imports: [ClientesComponent],
      providers: [
        provideRouter([]),
        { provide: ClientesService, useValue: service },
        { provide: AppDialogService, useValue: dialog },
        { provide: MatSnackBar, useValue: { open: jasmine.createSpy('open') } },
      ],
    });
  });

  it('pesquisa pelo texto e situação e apresenta as empresas associadas', fakeAsync(() => {
    service.list.and.returnValue(of([cliente]));
    const fixture = TestBed.createComponent(ClientesComponent);
    fixture.detectChanges();
    tick(300);
    fixture.componentInstance.searchControl.setValue('Empresa Local');
    fixture.componentInstance.statusControl.setValue('ACTIVE');
    tick(300);
    fixture.detectChanges();

    expect(service.list).toHaveBeenCalledWith('Empresa Local', 'ACTIVE');
    expect(fixture.nativeElement.textContent).toContain('Ana Lima');
    expect(fixture.nativeElement.textContent).toContain('Empresa Local');
    expect(fixture.nativeElement.textContent).toContain('Compradora');
    fixture.destroy();
  }));

  it('apresenta estado vazio quando a pesquisa não encontra clientes', fakeAsync(() => {
    service.list.and.returnValue(of([]));
    const fixture = TestBed.createComponent(ClientesComponent);
    fixture.detectChanges();
    tick(300);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Nenhum cliente encontrado');
    fixture.destroy();
  }));

  it('distingue falha da API do estado vazio e permite recarregar a listagem', fakeAsync(() => {
    service.list.and.returnValues(
      throwError(() => new HttpErrorResponse({ status: 503 })),
      of([cliente]),
    );
    const fixture = TestBed.createComponent(ClientesComponent);
    fixture.detectChanges();
    tick(300);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Não foi possível carregar os clientes');

    fixture.componentInstance.tentarNovamente();
    fixture.detectChanges();
    expect(fixture.componentInstance.clientes()).toEqual([cliente]);
    fixture.destroy();
  }));

  it('desativa cliente somente após confirmação', fakeAsync(() => {
    const confirmacao = new Subject<boolean | undefined>();
    service.list.and.returnValue(of([cliente]));
    service.changeStatus.and.returnValue(of({ ...cliente, status: 'INACTIVE' }));
    dialog.open.and.returnValue({ afterClosed: () => confirmacao } as never);
    const fixture = TestBed.createComponent(ClientesComponent);
    fixture.detectChanges();
    tick(300);

    fixture.componentInstance.alternarStatus(cliente);
    const argumentosDialogo = dialog.open.calls.mostRecent().args;
    expect(argumentosDialogo[0]).toBe(ConfirmacaoDialogComponent);
    expect(argumentosDialogo[1]).toEqual(jasmine.objectContaining({
      perigo: true, mensagem: jasmine.stringContaining(cliente.name),
    }));
    expect(service.changeStatus).not.toHaveBeenCalled();
    confirmacao.next(true);
    expect(service.changeStatus).toHaveBeenCalledOnceWith('cliente-1', 'INACTIVE');
    fixture.destroy();
  }));
});
