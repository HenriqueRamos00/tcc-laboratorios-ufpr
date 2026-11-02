import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { FormControl } from '@angular/forms';
import { OverlayContainer } from '@angular/cdk/overlay';
import { ReferenceSearchComponent } from './reference-search.component';

describe('Pesquisa de referências com painel sobreposto', () => {
  let fixture: ComponentFixture<ReferenceSearchComponent>;
  let component: ReferenceSearchComponent;
  let overlay: HTMLElement;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [ReferenceSearchComponent] });
    fixture = TestBed.createComponent(ReferenceSearchComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('control', new FormControl('', { nonNullable: true }));
    fixture.componentRef.setInput('fieldId', 'reference-test');
    fixture.componentRef.setInput('label', 'Referência');
    fixture.componentRef.setInput('options', [{ id: '7', label: 'A-7', description: 'Descrição' }]);
    overlay = TestBed.inject(OverlayContainer).getContainerElement();
    fixture.detectChanges();
  });

  it('abre opções fora do formulário e fecha após seleção explícita', () => {
    const selected = spyOn(component.selected, 'emit');
    component.opened.set(true);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.reference-option')).toBeNull();
    const option = overlay.querySelector<HTMLButtonElement>('.reference-option')!;
    option.click();
    fixture.detectChanges();
    expect(selected).toHaveBeenCalledWith('7');
    expect(component.opened()).toBeFalse();
  });

  it('mantém o campo montado durante a consulta e bloqueia seleção de dados antigos', () => {
    const field = fixture.nativeElement.querySelector('input');
    const selected = spyOn(component.selected, 'emit');
    fixture.componentRef.setInput('loading', true);
    fixture.detectChanges();
    component.select('7');
    expect(selected).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('input')).toBe(field);
    fixture.componentRef.setInput('loading', false);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('input')).toBe(field);
  });

  it('permite abrir por teclado e fechar com Escape devolvendo o foco', fakeAsync(() => {
    component.inputKeydown(new KeyboardEvent('keydown', { key: 'ArrowDown' }));
    fixture.detectChanges();
    tick();
    expect(document.activeElement).toBe(overlay.querySelector('.reference-option'));
    component.panelKeydown(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();
    expect(component.opened()).toBeFalse();
    expect(document.activeElement).toBe(fixture.nativeElement.querySelector('input'));
  }));
});
