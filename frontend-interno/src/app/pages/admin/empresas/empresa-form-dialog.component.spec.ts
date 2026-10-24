import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { of, throwError } from 'rxjs';

import { Empresa } from '@/app/model/empresa';
import { EmpresasService } from '@/app/services/empresas.service';
import { EmpresaFormDialogComponent } from './empresa-form-dialog.component';

describe('Formulário de empresa', () => {
  let service: jasmine.SpyObj<EmpresasService>;
  let dialogRef: jasmine.SpyObj<MatDialogRef<EmpresaFormDialogComponent>>;
  let fixture: ComponentFixture<EmpresaFormDialogComponent>;

  beforeEach(() => {
    service = jasmine.createSpyObj<EmpresasService>('EmpresasService', ['create', 'update']);
    dialogRef = jasmine.createSpyObj<MatDialogRef<EmpresaFormDialogComponent>>('MatDialogRef', ['close']);
  });

  function criarComponente(empresa: Empresa | null) {
    TestBed.configureTestingModule({
      imports: [EmpresaFormDialogComponent],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: { empresa } },
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: EmpresasService, useValue: service },
      ],
    });
    fixture = TestBed.createComponent(EmpresaFormDialogComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  function preencher(component: EmpresaFormDialogComponent): void {
    component.form.setValue({
      legalName: ' Empresa de Teste ', cnpj: '04.368.898/0001-06', address: ' Rua Um, 10 ', city: ' Curitiba ',
      state: 'pr', contactName: ' Ana Lima ', contactPhone: ' (41) 3333-4444 ', contactEmail: ' ANA@EXAMPLE.COM ',
    });
  }

  it('aplica a máscara ao digitar, colar e apagar o CNPJ', () => {
    const component = criarComponente(null);
    const input: HTMLInputElement = fixture.nativeElement.querySelector('#empresa-cnpj');
    for (const [value, expected] of [
      ['043', '04.3'], ['04368898', '04.368.898'], ['04368898000106', '04.368.898/0001-06'],
      ['04.368.898/0001-06', '04.368.898/0001-06'], ['abc0436889800010699', '04.368.898/0001-06'],
      ['04.368.898/0001-0', '04.368.898/0001-0'], ['', ''],
    ]) {
      input.value = value;
      input.dispatchEvent(new Event('input'));
      expect(input.value).toBe(expected);
      expect(component.form.controls.cnpj.value).toBe(expected.replace(/\D/g, ''));
    }
  });

  it('preserva a posição do cursor ao editar no meio do CNPJ', () => {
    criarComponente(null);
    const input: HTMLInputElement = fixture.nativeElement.querySelector('#empresa-cnpj');
    input.value = '04.368.898/0001-06';
    input.setSelectionRange(5, 5);
    input.dispatchEvent(new Event('input'));
    expect(input.selectionStart).toBe(5);
  });

  it('exibe o CNPJ existente com máscara ao editar a empresa', async () => {
    const component = criarComponente({
      id: 'empresa-1', legalName: 'Empresa', cnpj: '04368898000106', address: 'Rua Um, 10', city: 'Curitiba',
      state: 'PR', contactName: 'Ana', contactPhone: '4133334444', contactEmail: 'ana@example.com',
      status: 'ACTIVE', origin: 'LOCAL',
    });
    await fixture.whenStable();
    const input: HTMLInputElement = fixture.nativeElement.querySelector('#empresa-cnpj');
    expect(input.value).toBe('04.368.898/0001-06');
    expect(component.form.controls.cnpj.valid).toBeTrue();
  });

  it('exige todos os campos antes de cadastrar', () => {
    const component = criarComponente(null);
    component.save();

    expect(component.form.invalid).toBeTrue();
    expect(service.create).not.toHaveBeenCalled();
  });

  it('valida CNPJ, e-mail e UF antes de enviar os dados', () => {
    const component = criarComponente(null);
    preencher(component);
    component.form.controls.cnpj.setValue('11.111.111/1111-11');
    component.form.controls.contactEmail.setValue('email inválido');
    component.form.controls.state.setValue('XX');
    component.save();

    expect(component.form.invalid).toBeTrue();
    expect(service.create).not.toHaveBeenCalled();
  });

  it('normaliza CNPJ, UF e e-mail no cadastro sem alterar os valores digitados antes de salvar', () => {
    service.create.and.returnValue(of({
      id: 'empresa-1', legalName: 'Empresa de Teste', cnpj: '04368898000106', address: 'Rua Um, 10', city: 'Curitiba',
      state: 'PR', contactName: 'Ana Lima', contactPhone: '(41) 3333-4444', contactEmail: 'ana@example.com',
      status: 'ACTIVE', origin: 'LOCAL',
    }));
    const component = criarComponente(null);
    preencher(component);
    expect(component.form.valid).toBeTrue();
    component.save();

    expect(service.create).toHaveBeenCalledWith({
      legalName: 'Empresa de Teste', cnpj: '04368898000106', address: 'Rua Um, 10', city: 'Curitiba', state: 'PR',
      contactName: 'Ana Lima', contactPhone: '4133334444', contactEmail: 'ana@example.com', status: 'ACTIVE',
    });
    expect(dialogRef.close).toHaveBeenCalled();
  });

  it('cancela sem enviar alterações para a API', () => {
    const component = criarComponente(null);
    component.form.controls.legalName.setValue('Alteração descartada');
    component.close();

    expect(service.create).not.toHaveBeenCalled();
    expect(service.update).not.toHaveBeenCalled();
    expect(dialogRef.close).toHaveBeenCalledWith();
  });

  it('mantém o formulário aberto com os dados preenchidos quando o CNPJ já existe', () => {
    service.create.and.returnValue(throwError(() => new HttpErrorResponse({
      status: 409, statusText: 'Conflict', error: { message: 'CNPJ duplicado' },
    })));
    const component = criarComponente(null);
    preencher(component);
    component.save();

    expect(component.serverError()).toContain('já está cadastrado');
    expect(component.form.controls.legalName.value).toBe(' Empresa de Teste ');
    expect(dialogRef.close).not.toHaveBeenCalled();
  });
});
