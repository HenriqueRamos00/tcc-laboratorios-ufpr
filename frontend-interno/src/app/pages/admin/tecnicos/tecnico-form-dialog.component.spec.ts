import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { of, throwError } from 'rxjs';

import { TecnicosService } from '@/app/services/tecnicos.service';
import { TecnicoFormDialogComponent } from './tecnico-form-dialog.component';

describe('Formulário de técnico', () => {
  let service: jasmine.SpyObj<TecnicosService>;
  let dialogRef: jasmine.SpyObj<MatDialogRef<TecnicoFormDialogComponent>>;

  beforeEach(() => {
    service = jasmine.createSpyObj<TecnicosService>('TecnicosService', ['create', 'update']);
    dialogRef = jasmine.createSpyObj<MatDialogRef<TecnicoFormDialogComponent>>('MatDialogRef', ['close']);
  });

  function createComponent(tecnico: null | {
    id: number; name: string; email: string; unit: string; specialty: string; status: 'ACTIVE' | 'INACTIVE';
  }) {
    TestBed.configureTestingModule({
      imports: [TecnicoFormDialogComponent],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: { tecnico } },
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: TecnicosService, useValue: service },
      ],
    });
    const fixture = TestBed.createComponent(TecnicoFormDialogComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('exige a senha inicial e todos os campos no cadastro', () => {
    const component = createComponent(null);
    component.save();

    expect(component.form.invalid).toBeTrue();
    expect(service.create).not.toHaveBeenCalled();
  });

  it('remove espaços dos dados do técnico e valida o e-mail após a remoção no cadastro', () => {
    service.create.and.returnValue(of({
      id: 12, name: 'Maria Santos', email: 'maria@example.com', unit: 'DOLEO', specialty: 'Óleos', status: 'ACTIVE',
    }));
    const component = createComponent(null);
    component.form.setValue({
      name: ' Maria Santos ', email: ' MARIA@EXAMPLE.COM ', unit: ' DOLEO ', specialty: ' Óleos ',
      status: 'ACTIVE', password: 'senha',
    });

    expect(component.form.valid).toBeTrue();
    component.save();

    expect(service.create).toHaveBeenCalledWith({
      name: 'Maria Santos', email: 'maria@example.com', unit: 'DOLEO', specialty: 'Óleos',
      status: 'ACTIVE', password: 'senha',
    });
  });

  it('cancela sem enviar alterações para a API', () => {
    const component = createComponent(null);
    component.form.controls.name.setValue('Alteração que será descartada');
    component.close();

    expect(service.create).not.toHaveBeenCalled();
    expect(service.update).not.toHaveBeenCalled();
    expect(dialogRef.close).toHaveBeenCalledWith();
  });

  it('omite a senha vazia da requisição de edição', () => {
    service.update.and.returnValue(of({
      id: 12, name: 'Maria Santos', email: 'maria@example.com', unit: 'DOLEO', specialty: 'Óleos', status: 'ACTIVE',
    }));
    const component = createComponent({
      id: 12, name: 'Maria Santos', email: 'maria@example.com', unit: 'DOLEO', specialty: 'Óleos', status: 'ACTIVE',
    });
    component.form.controls.specialty.setValue('Análise de Óleos');
    component.save();

    expect(service.update).toHaveBeenCalledWith(12, {
      name: 'Maria Santos', email: 'maria@example.com', unit: 'DOLEO', specialty: 'Análise de Óleos', status: 'ACTIVE',
    });
    expect(dialogRef.close).toHaveBeenCalled();
  });

  it('exige confirmação pela lista para desativar uma conta ativa', () => {
    const component = createComponent({
      id: 12, name: 'Maria Santos', email: 'maria@example.com', unit: 'DOLEO', specialty: 'Óleos', status: 'ACTIVE',
    });

    expect(component.form.controls.status.disabled).toBeTrue();
    expect(component.form.getRawValue().status).toBe('ACTIVE');
  });

  it('permite reativar uma conta inativa pelo formulário de edição', () => {
    const component = createComponent({
      id: 12, name: 'Maria Santos', email: 'maria@example.com', unit: 'DOLEO', specialty: 'Óleos', status: 'INACTIVE',
    });

    expect(component.form.controls.status.enabled).toBeTrue();
    component.form.controls.status.setValue('ACTIVE');
    expect(component.form.getRawValue().status).toBe('ACTIVE');
  });

  it('mantém o formulário aberto e os valores após falha de validação no servidor', () => {
    service.create.and.returnValue(throwError(() => new HttpErrorResponse({
      status: 409, statusText: 'Conflict', error: { message: 'E-mail duplicado' },
    })));
    const component = createComponent(null);
    component.form.setValue({
      name: 'Maria Santos', email: 'maria@example.com', unit: 'DOLEO', specialty: 'Óleos', status: 'ACTIVE', password: 'senha',
    });
    component.save();

    expect(component.serverError()).toContain('já está cadastrado');
    expect(component.form.controls.name.value).toBe('Maria Santos');
    expect(dialogRef.close).not.toHaveBeenCalled();
  });
});
