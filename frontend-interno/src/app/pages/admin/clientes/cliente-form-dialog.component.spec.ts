import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { of, throwError } from 'rxjs';

import { Cliente } from '@/app/model/cliente';
import { Empresa } from '@/app/model/empresa';
import { ClientesService } from '@/app/services/clientes.service';
import { EmpresasService } from '@/app/services/empresas.service';
import { ClienteFormDialogComponent } from './cliente-form-dialog.component';

describe('Formulário de cliente', () => {
  let clients: jasmine.SpyObj<ClientesService>;
  let companies: jasmine.SpyObj<EmpresasService>;
  let formulario: HTMLElement;
  let dialogRef: jasmine.SpyObj<MatDialogRef<ClienteFormDialogComponent>>;

  const empresaAtiva: Empresa = {
    id: 'empresa-ativa', legalName: 'Empresa Ativa', cnpj: '04368898000106', address: 'Rua Um, 10', city: 'Curitiba',
    state: 'PR', contactName: 'Ana Lima', contactPhone: '4133334444', contactEmail: 'ana@example.com', status: 'ACTIVE', origin: 'LOCAL',
  };
  const empresaInativa: Empresa = {
    ...empresaAtiva, id: 'empresa-inativa', legalName: 'Empresa Inativa', status: 'INACTIVE',
  };
  const segundaEmpresaAtiva: Empresa = {
    ...empresaAtiva, id: 'empresa-ativa-2', legalName: 'Segunda Empresa Ativa',
  };

  beforeEach(() => {
    clients = jasmine.createSpyObj<ClientesService>('ClientesService', ['create', 'update']);
    companies = jasmine.createSpyObj<EmpresasService>('EmpresasService', ['list']);
    companies.list.and.returnValue(of([empresaAtiva, empresaInativa, segundaEmpresaAtiva]));
    dialogRef = jasmine.createSpyObj<MatDialogRef<ClienteFormDialogComponent>>('MatDialogRef', ['close']);
  });

  function criarComponente(cliente: Cliente | null) {
    TestBed.configureTestingModule({
      imports: [ClienteFormDialogComponent],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: { cliente } },
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: ClientesService, useValue: clients },
        { provide: EmpresasService, useValue: companies },
      ],
    });
    const fixture = TestBed.createComponent(ClienteFormDialogComponent);
    fixture.detectChanges();
    formulario = fixture.nativeElement;
    return fixture.componentInstance;
  }

  function preencher(component: ClienteFormDialogComponent, password = 'senha segura'): void {
    component.form.patchValue({
      name: ' Ana Lima ', email: ' ANA@EXAMPLE.COM ', phone: ' (41) 3333-4444 ', position: ' Compradora ',
      status: 'ACTIVE', companyIds: ['empresa-ativa'], password,
    });
  }

  it('exige todos os campos, a senha inicial e pelo menos uma empresa ativa', () => {
    const component = criarComponente(null);
    component.save();

    expect(component.form.invalid).toBeTrue();
    expect(clients.create).not.toHaveBeenCalled();
  });

  it('cria com múltiplas empresas, dados normalizados e preserva espaços da senha', () => {
    clients.create.and.returnValue(of({
      id: 'cliente-1', name: 'Ana Lima', email: 'ana@example.com', phone: '(41) 3333-4444', position: 'Compradora',
      status: 'ACTIVE', companies: [{ id: 'empresa-ativa', name: 'Empresa Ativa' }],
    }));
    const component = criarComponente(null);
    preencher(component, '  senha inicial  ');
    component.form.controls.companyIds.setValue(['empresa-ativa', 'empresa-ativa-2']);

    expect(component.form.valid).toBeTrue();
    component.save();

    expect(clients.create).toHaveBeenCalledWith({
      name: 'Ana Lima', email: 'ana@example.com', phone: '(41) 3333-4444', position: 'Compradora', status: 'ACTIVE',
      companyIds: ['empresa-ativa', 'empresa-ativa-2'], password: '  senha inicial  ',
    });
    expect(dialogRef.close).toHaveBeenCalled();
  });

  it('impede selecionar nova empresa inativa e não permite salvar sem vínculo ativo', () => {
    const component = criarComponente(null);
    preencher(component);
    expect(component.companyOptions().map((empresa) => empresa.id)).toEqual(['empresa-ativa', 'empresa-ativa-2']);

    component.form.controls.companyIds.setValue([]);
    component.save();
    expect(component.form.controls.companyIds.hasError('activeCompanyRequired')).toBeTrue();
    expect(clients.create).not.toHaveBeenCalled();
  });

  it('mantém uma empresa inativa já vinculada na edição e exige pelo menos um vínculo ativo', () => {
    const cliente: Cliente = {
      id: 'cliente-2', name: 'Ana Lima', email: 'ana@example.com', phone: '4133334444', position: 'Compradora', status: 'ACTIVE',
      companies: [
        { id: 'empresa-ativa', name: 'Empresa Ativa', status: 'ACTIVE' },
        { id: 'empresa-inativa', name: 'Empresa Inativa', status: 'INACTIVE' },
      ],
    };
    const component = criarComponente(cliente);
    const inativa = component.companyOptions().find((empresa) => empresa.id === 'empresa-inativa');
    expect(inativa).toBeDefined();
    expect(component.empresaMarcada('empresa-inativa')).toBeTrue();
    const checkbox = formulario.querySelector<HTMLInputElement>(
      'input[aria-label="Empresa Inativa, inativa já vinculada"]',
    )!;
    checkbox.click();
    expect(component.form.controls.companyIds.value).toEqual(['empresa-ativa']);
    expect(component.form.controls.companyIds.valid).toBeTrue();
    checkbox.click();
    expect(component.empresaMarcada('empresa-inativa')).toBeTrue();

    component.form.controls.companyIds.setValue(['empresa-inativa']);
    component.form.controls.companyIds.markAsTouched();
    component.save();

    expect(component.form.controls.companyIds.hasError('activeCompanyRequired')).toBeTrue();
    expect(clients.update).not.toHaveBeenCalled();
  });

  it('omite senha vazia na edição e envia os vínculos que continuam selecionados', () => {
    const cliente: Cliente = {
      id: 'cliente-2', name: 'Ana Lima', email: 'ana@example.com', phone: '4133334444', position: 'Compradora', status: 'ACTIVE',
      companies: [{ id: 'empresa-ativa', name: 'Empresa Ativa', status: 'ACTIVE' }],
    };
    clients.update.and.returnValue(of(cliente));
    const component = criarComponente(cliente);
    component.form.controls.position.setValue('Compradora Sênior');
    component.save();

    expect(clients.update).toHaveBeenCalledWith('cliente-2', {
      name: 'Ana Lima', email: 'ana@example.com', phone: '4133334444', position: 'Compradora Sênior', status: 'ACTIVE',
      companyIds: ['empresa-ativa'],
    });
    expect(dialogRef.close).toHaveBeenCalled();
  });

  it('rejeita senha formada apenas por espaços', () => {
    const component = criarComponente(null);
    preencher(component, '   ');

    component.save();

    expect(component.form.controls.password.hasError('pattern')).toBeTrue();
    expect(clients.create).not.toHaveBeenCalled();
  });

  it('rejeita e-mail formado apenas por espaços antes de enviar os dados', () => {
    const component = criarComponente(null);
    preencher(component);
    component.form.controls.email.setValue('   ');

    component.save();

    expect(component.form.controls.email.hasError('email')).toBeTrue();
    expect(clients.create).not.toHaveBeenCalled();
  });

  it('limita a senha a 72 caracteres', () => {
    const component = criarComponente(null);
    preencher(component, 'a'.repeat(72));
    expect(component.form.controls.password.valid).toBeTrue();
    component.form.controls.password.setValue('a'.repeat(73));
    expect(component.form.controls.password.hasError('maxlength')).toBeTrue();
    component.save();
    expect(clients.create).not.toHaveBeenCalled();
  });

  it('mantém o formulário aberto e os valores após duplicidade de e-mail', () => {
    clients.create.and.returnValue(throwError(() => new HttpErrorResponse({
      status: 409, statusText: 'Conflict', error: { message: 'E-mail duplicado' },
    })));
    const component = criarComponente(null);
    preencher(component);
    component.save();

    expect(component.serverError()).toContain('já está cadastrado');
    expect(component.form.controls.name.value).toBe(' Ana Lima ');
    expect(dialogRef.close).not.toHaveBeenCalled();
  });
});
