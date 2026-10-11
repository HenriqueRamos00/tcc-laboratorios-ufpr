import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { of, Subject, throwError } from 'rxjs';

import { Equipamento, EquipamentoRequest } from '@/app/model/equipamento';
import { EquipamentosService } from '@/app/services/equipamentos.service';
import { EquipamentoFormDialogComponent } from './equipamento-form-dialog.component';

describe('Formulário de equipamento', () => {
  const fields: EquipamentoRequest = {
    serialNumber: '00099001', tag: 'TR-C10', type: 'TRANSFORMADOR', substation: 'SE Norte',
    primaryVoltage: '13.8', owner: 'UFPR', riskLevel: 'SAUDAVEL',
  };
  let service: jasmine.SpyObj<EquipamentosService>;
  let dialogRef: jasmine.SpyObj<MatDialogRef<EquipamentoFormDialogComponent>>;

  beforeEach(() => {
    service = jasmine.createSpyObj<EquipamentosService>('EquipamentosService', ['create', 'update']);
    dialogRef = jasmine.createSpyObj<MatDialogRef<EquipamentoFormDialogComponent>>('MatDialogRef', ['close']);
  });

  function createComponent(equipamento: Equipamento | null = null) {
    TestBed.configureTestingModule({
      imports: [EquipamentoFormDialogComponent],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: { equipamento } },
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: EquipamentosService, useValue: service },
      ],
    });
    const fixture = TestBed.createComponent(EquipamentoFormDialogComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('começa vazio e exige todos os sete campos', () => {
    const fixture = createComponent();
    const component = fixture.componentInstance;
    expect(Object.values(component.form.getRawValue()).every((value) => value === '')).toBeTrue();
    component.save();
    expect(component.form.invalid).toBeTrue();
    expect(Object.values(component.form.controls).every((control) => control.invalid && control.touched)).toBeTrue();
    expect(service.create).not.toHaveBeenCalled();
  });

  it('preenche a edição sem modificar a linha ao cancelar', () => {
    const equipamento = { id: 12, ...fields };
    const component = createComponent(equipamento).componentInstance;
    expect(component.form.getRawValue()).toEqual(fields);
    component.form.controls.tag.setValue('TR-B08');
    component.close();
    expect(equipamento.tag).toBe('TR-C10');
    expect(service.update).not.toHaveBeenCalled();
    expect(service.create).not.toHaveBeenCalled();
    expect(dialogRef.close).toHaveBeenCalledWith();
  });

  for (const [field, limit] of [['serialNumber', 100], ['tag', 100], ['substation', 150], ['owner', 200]] as const) {
    it(`valida ${field} após remover espaços e aplica seu limite`, () => {
      const component = createComponent().componentInstance;
      const control = component.form.controls[field];
      control.setValue('   ');
      expect(control.invalid).toBeTrue();
      control.setValue(' ' + 'a'.repeat(limit) + ' ');
      expect(control.valid).toBeTrue();
      control.setValue('a'.repeat(limit + 1));
      expect(control.invalid).toBeTrue();
    });
  }

  it('valida tensão positiva e precisão com ponto ou vírgula', () => {
    const control = createComponent().componentInstance.form.controls.primaryVoltage;
    for (const value of ['', ' ', '0', '0.000', '-1', '1000000', '1.0001', '1e3', 'NaN', '1,2,3', 'abc']) {
      control.setValue(value);
      expect(control.invalid).withContext(value).toBeTrue();
    }
    for (const value of ['0.001', '13,8', '999999.999', ' 138.250 ']) {
      control.setValue(value);
      expect(control.valid).withContext(value).toBeTrue();
    }
  });

  it('normaliza textos e vírgula, preserva zeros e fecha com o equipamento criado', () => {
    const saved = { id: 12, ...fields };
    service.create.and.returnValue(of(saved));
    const component = createComponent().componentInstance;
    component.form.setValue({ ...fields, serialNumber: ' 00099001 ', tag: ' TR-C10 ', owner: ' UFPR ', substation: ' SE Norte ', primaryVoltage: ' 13,8 ' });
    component.save();
    expect(service.create).toHaveBeenCalledOnceWith(fields);
    expect(dialogRef.close).toHaveBeenCalledOnceWith(saved);
  });

  it('envia todos os campos editáveis na atualização', () => {
    const updated = { id: 12, ...fields, tag: 'TR-B08', riskLevel: 'ATENCAO' as const };
    service.update.and.returnValue(of(updated));
    const component = createComponent({ id: 12, ...fields }).componentInstance;
    component.form.patchValue({ tag: 'TR-B08', riskLevel: 'ATENCAO' });
    component.save();
    expect(service.update).toHaveBeenCalledOnceWith(12, { ...fields, tag: 'TR-B08', riskLevel: 'ATENCAO' });
    expect(service.create).not.toHaveBeenCalled();
    expect(dialogRef.close).toHaveBeenCalledOnceWith(updated);
  });

  for (const editing of [false, true]) {
    it(`preserva a entrada e permite tentar novamente após falha de ${editing ? 'edição' : 'criação'}`, () => {
      const failure = throwError(() => new HttpErrorResponse({ status: 400, error: { message: 'Tensão inválida' } }));
      service.create.and.returnValue(failure);
      service.update.and.returnValue(failure);
      const component = createComponent(editing ? { id: 12, ...fields } : null).componentInstance;
      const entered = { ...fields, primaryVoltage: '13,8', owner: ' UFPR ' };
      component.form.setValue(entered);
      component.save();
      expect(component.form.getRawValue()).toEqual(entered);
      expect(component.serverError()).toBe('Tensão inválida');
      expect(component.saving()).toBeFalse();
      expect(dialogRef.disableClose).toBeFalse();
      expect(dialogRef.close).not.toHaveBeenCalled();
      component.save();
      expect(editing ? service.update : service.create).toHaveBeenCalledTimes(2);
    });
  }

  it('bloqueia envio duplicado, cancelamento e fechamento durante a gravação', () => {
    const pending = new Subject<Equipamento>();
    service.create.and.returnValue(pending);
    const fixture = createComponent();
    const component = fixture.componentInstance;
    component.form.setValue(fields);
    component.save();
    component.save();
    component.close();
    fixture.detectChanges();
    expect(service.create).toHaveBeenCalledTimes(1);
    expect(component.saving()).toBeTrue();
    expect(dialogRef.disableClose).toBeTrue();
    expect(dialogRef.close).not.toHaveBeenCalled();
    for (const button of Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[]) {
      expect(button.disabled).toBeTrue();
    }
    expect((fixture.nativeElement.querySelector('fieldset') as HTMLFieldSetElement).disabled).toBeTrue();
    pending.next({ id: 12, ...fields });
    pending.complete();
    expect(dialogRef.close).toHaveBeenCalledOnceWith({ id: 12, ...fields });
  });
});
