import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject, of } from 'rxjs';
import { EnsaiosService } from '@/app/services/ensaios.service';
import { EnsaiosComponent } from './ensaios.component';

describe('Listagem de ensaios por orçamento', () => {
  it('preserva o filtro na busca e paginação e permite voltar a todos os ensaios', fakeAsync(() => {
    const parametros = new BehaviorSubject(convertToParamMap({ quoteId: '8', quoteCode: 'ORC-8' }));
    const service = jasmine.createSpyObj<EnsaiosService>('EnsaiosService', ['list']);
    service.list.and.returnValue(of({ items: [], totalItems: 40, page: 1, pageSize: 20 }));
    TestBed.configureTestingModule({
      imports: [EnsaiosComponent],
      providers: [
        provideRouter([]),
        { provide: EnsaiosService, useValue: service },
        { provide: ActivatedRoute, useValue: { queryParamMap: parametros } },
      ],
    });
    const fixture = TestBed.createComponent(EnsaiosComponent);
    fixture.detectChanges();
    tick(300);
    expect(service.list).toHaveBeenCalledWith('', 1, 20, '8');
    fixture.componentInstance.setPage(2);
    expect(service.list).toHaveBeenCalledWith('', 2, 20, '8');
    fixture.componentInstance.search.setValue('óleo');
    tick(300);
    expect(service.list).toHaveBeenCalledWith('óleo', 1, 20, '8');
    parametros.next(convertToParamMap({}));
    expect(service.list).toHaveBeenCalledWith('óleo', 1, 20, undefined);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('Ensaios do orçamento');
    fixture.destroy();
  }));
});
