import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { MatDialogRef } from '@angular/material/dialog';
import { Subject, throwError } from 'rxjs';
import { InstrumentReference } from '@/app/model/ensaio';
import { EnsaiosService } from '@/app/services/ensaios.service';
import { InstrumentoFormDialogComponent } from './instrumento-form-dialog.component';

describe('Drawer de cadastro de instrumento', () => {
  let service: jasmine.SpyObj<EnsaiosService>;
  let dialogRef: jasmine.SpyObj<MatDialogRef<InstrumentoFormDialogComponent>>;
  beforeEach(() => {
    service = jasmine.createSpyObj('EnsaiosService', ['createInstrument']);
    dialogRef = jasmine.createSpyObj('MatDialogRef', ['close']);
    TestBed.configureTestingModule({ imports: [InstrumentoFormDialogComponent], providers: [
      { provide: EnsaiosService, useValue: service }, { provide: MatDialogRef, useValue: dialogRef },
    ] });
  });

  it('bloqueia envio repetido e devolve o instrumento criado', () => {
    const response = new Subject<InstrumentReference>();
    service.createInstrument.and.returnValue(response);
    const component = TestBed.createComponent(InstrumentoFormDialogComponent).componentInstance;
    component.form.patchValue({ inventoryNumber: ' INS-1 ', name: ' Balança ' });
    component.save(); component.save();
    expect(service.createInstrument).toHaveBeenCalledOnceWith({
      inventoryNumber: 'INS-1', name: 'Balança', calibrationCertificate: null, calibrationValidUntil: null,
    });
    expect(dialogRef.disableClose).toBeTrue();
    const instrument = { id: '1', inventoryNumber: 'INS-1', name: 'Balança', calibrationCertificate: null, calibrationValidUntil: null };
    response.next(instrument);
    expect(dialogRef.close).toHaveBeenCalledWith(instrument);
  });

  it('preserva dados e libera nova tentativa quando o patrimônio já existe', () => {
    service.createInstrument.and.returnValue(throwError(() => new HttpErrorResponse({
      status: 409, error: { message: 'O patrimônio já está cadastrado' },
    })));
    const component = TestBed.createComponent(InstrumentoFormDialogComponent).componentInstance;
    component.form.patchValue({ inventoryNumber: 'INS-1', name: 'Balança' });
    component.save();
    expect(component.error()).toBe('O patrimônio já está cadastrado');
    expect(component.form.controls.name.value).toBe('Balança');
    expect(component.saving()).toBeFalse();
    expect(dialogRef.disableClose).toBeFalse();
    expect(dialogRef.close).not.toHaveBeenCalled();
  });
});
