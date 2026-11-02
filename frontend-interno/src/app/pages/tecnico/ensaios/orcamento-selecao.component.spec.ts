import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { of, Subject, throwError } from 'rxjs';
import { EnsaiosService } from '@/app/services/ensaios.service';
import { QuoteReference } from '@/app/model/ensaio';
import { OrcamentoSelecaoComponent } from './orcamento-selecao.component';

const quote: QuoteReference = { id: '1', code: 'ORC-001', companyId: '4', companyName: 'Empresa de teste' };
const result = { items: [quote], page: 1, pageSize: 20, totalItems: 21 };

describe('Seleção de orçamento no painel lateral', () => {
  let fixture: ComponentFixture<OrcamentoSelecaoComponent>;
  let component: OrcamentoSelecaoComponent;
  let service: jasmine.SpyObj<EnsaiosService>;
  let ref: jasmine.SpyObj<MatDialogRef<OrcamentoSelecaoComponent>>;

  beforeEach(() => {
    service = jasmine.createSpyObj('EnsaiosService', ['quotes']);
    service.quotes.and.returnValue(of(result));
    ref = jasmine.createSpyObj('MatDialogRef', ['close']);
    TestBed.configureTestingModule({
      imports: [OrcamentoSelecaoComponent],
      providers: [
        { provide: EnsaiosService, useValue: service },
        { provide: MatDialogRef, useValue: ref },
        { provide: MAT_DIALOG_DATA, useValue: null },
      ],
    });
  });

  function createComponent(): void {
    fixture = TestBed.createComponent(OrcamentoSelecaoComponent);
    component = fixture.componentInstance;
  }

  it('cancela respostas antigas durante a digitação e pesquisa na primeira página', fakeAsync(() => {
    const old = new Subject<typeof result>();
    createComponent();
    tick(0);
    component.setPage(2);
    service.quotes.and.returnValue(old);
    component.search.setValue('antigo');
    tick(300);
    service.quotes.and.returnValue(of({ ...result, items: [{ ...quote, id: '2', code: 'NOVO' }] }));
    component.search.setValue('novo');
    expect(component.loading()).toBeTrue();
    old.next(result);
    expect(component.loading()).toBeTrue();
    tick(300);
    expect(service.quotes).toHaveBeenCalledWith('novo', 1, 20);
    expect(component.items()[0].code).toBe('NOVO');
  }));

  it('pagina as referências e só retorna uma seleção depois da resposta', fakeAsync(() => {
    createComponent();
    tick(0);
    const pending = new Subject<typeof result>();
    service.quotes.and.returnValue(pending);
    component.setPage(2);
    expect(service.quotes).toHaveBeenCalledWith('', 2, 20);
    component.select(quote);
    expect(ref.close).not.toHaveBeenCalled();
    pending.next({ ...result, page: 2 });
    component.select(quote);
    expect(ref.close).toHaveBeenCalledWith(quote);
  }));

  it('distingue erro de ausência e permite repetir a consulta', fakeAsync(() => {
    service.quotes.and.returnValue(throwError(() => new Error('Falha')));
    createComponent();
    tick(0);
    expect(component.error()).toBeTrue();
    component.select(quote);
    expect(ref.close).not.toHaveBeenCalled();
    service.quotes.and.returnValue(of({ ...result, items: [], totalItems: 0 }));
    component.retry();
    expect(component.error()).toBeFalse();
    expect(component.items()).toEqual([]);
  }));
});
