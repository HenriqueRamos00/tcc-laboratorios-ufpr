import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { of } from 'rxjs';
import { EnsaiosService } from '@/app/services/ensaios.service';
import { NovaAmostraDialogComponent } from './nova-amostra-dialog.component';

const quote = { id: '8', code: 'O-8', companyId: '4', companyName: 'Empresa' };
const equipment = { id: '5', tag: 'TR-5', serialNumber: 'S-5' };

describe('Drawer de identificação da amostra', () => {
  let service: jasmine.SpyObj<EnsaiosService>;
  let dialogRef: jasmine.SpyObj<MatDialogRef<NovaAmostraDialogComponent>>;
  beforeEach(() => {
    service = jasmine.createSpyObj('EnsaiosService', ['samples', 'analyzedEquipment']);
    service.samples.and.returnValue(of({ items: [], page: 1, pageSize: 20, totalItems: 0 }));
    service.analyzedEquipment.and.returnValue(of({ items: [equipment], page: 1, pageSize: 20, totalItems: 1 }));
    dialogRef = jasmine.createSpyObj('MatDialogRef', ['close']);
    TestBed.configureTestingModule({ imports: [NovaAmostraDialogComponent], providers: [
      { provide: EnsaiosService, useValue: service },
      { provide: MatDialogRef, useValue: dialogRef },
      { provide: MAT_DIALOG_DATA, useValue: { quote, code: '', description: '', equipment: null } },
    ] });
  });

  it('devolve identificação e equipamento sem persistir antes de salvar o ensaio', fakeAsync(() => {
    const component = TestBed.createComponent(NovaAmostraDialogComponent).componentInstance;
    tick();
    component.form.setValue({ code: ' A-1 ', description: ' Óleo ' });
    tick(300);
    component.selectEquipment('5');
    component.confirm();
    expect(service.analyzedEquipment).toHaveBeenCalledWith('8', '', 1, 20);
    expect(dialogRef.close).toHaveBeenCalledWith({ newSample: {
      quoteId: '8', code: 'A-1', description: 'Óleo', analyzedEquipmentId: '5',
    }, equipment });
    tick(300);
  }));

  it('impede confirmação de código existente e permite selecionar a amostra encontrada', fakeAsync(() => {
    const sample = { id: '20', code: 'A-1', description: 'Óleo', quote, analyzedEquipment: null };
    service.samples.and.returnValue(of({ items: [sample], page: 1, pageSize: 20, totalItems: 1 }));
    const component = TestBed.createComponent(NovaAmostraDialogComponent).componentInstance;
    component.form.setValue({ code: 'A-1', description: 'Nova descrição' });
    component.confirm();
    expect(dialogRef.close).not.toHaveBeenCalled();
    tick(300);
    expect(component.duplicate).toBeTrue();
    component.confirm();
    expect(dialogRef.close).not.toHaveBeenCalled();
    component.selectSample('20');
    expect(dialogRef.close).toHaveBeenCalledWith({ sample });
  }));
});
