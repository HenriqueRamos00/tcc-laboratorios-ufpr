import { ComponentFixture, fakeAsync, flushMicrotasks, TestBed, tick } from '@angular/core/testing';
import { FormControl } from '@angular/forms';
import { OverlayContainer } from '@angular/cdk/overlay';
import { ReferenceSearchComponent } from './reference-search.component';

describe('Pesquisa de referências com painel sobreposto', () => {
  let fixture: ComponentFixture<ReferenceSearchComponent>;
  let component: ReferenceSearchComponent;
  let overlay: HTMLElement;
  let outside: HTMLButtonElement;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [ReferenceSearchComponent] });
    fixture = TestBed.createComponent(ReferenceSearchComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('control', new FormControl('', { nonNullable: true }));
    fixture.componentRef.setInput('fieldId', 'reference-test');
    fixture.componentRef.setInput('label', 'Referência');
    fixture.componentRef.setInput('options', [
      { id: '7', label: 'A-7', description: 'Descrição' },
      { id: '8', label: 'A-8', description: 'Outra descrição' },
    ]);
    overlay = TestBed.inject(OverlayContainer).getContainerElement();
    outside = document.createElement('button');
    outside.textContent = 'Fora do seletor';
    document.body.append(outside);
    fixture.detectChanges();
  });

  afterEach(() => outside.remove());

  it('preserva o painel quando focusout informa uma opção como destino do foco', fakeAsync(() => {
    const selected = spyOn(component.selected, 'emit');
    openPanel();
    const field = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    const option = overlay.querySelector<HTMLButtonElement>('.reference-option')!;

    outside.focus();
    field.dispatchEvent(new FocusEvent('focusout', { bubbles: true, relatedTarget: option }));
    flushMicrotasks();
    expect(component.opened()).toBeTrue();
    expect(overlay.contains(option)).toBeTrue();

    field.focus();
    option.focus();
    expect(component.opened()).toBeTrue();
    option.click();
    fixture.detectChanges();

    expect(selected).toHaveBeenCalledWith('7');
    expect(component.opened()).toBeFalse();
    expect(document.activeElement).toBe(field);
  }));

  it('mantém o painel ao mover o foco da opção para controles de paginação', () => {
    fixture.componentRef.setInput('page', 1);
    fixture.componentRef.setInput('pages', 2);
    openPanel();
    const option = overlay.querySelector<HTMLButtonElement>('.reference-option')!;
    const nextPage = overlay.querySelector<HTMLButtonElement>('button[mat-button]:not(:disabled)')!;
    const checkFocus = spyOn(component, 'checkFocus').and.callThrough();

    option.dispatchEvent(new FocusEvent('focusout', { bubbles: true, relatedTarget: nextPage }));
    fixture.detectChanges();

    expect(checkFocus).toHaveBeenCalled();
    expect(checkFocus.calls.mostRecent().args[0].relatedTarget).toBe(nextPage);
    expect(component.opened()).toBeTrue();
    expect(overlay.querySelector('.reference-panel')).not.toBeNull();
  });

  it('fecha quando focusout informa um controle externo como destino do foco', () => {
    openPanel();
    const option = overlay.querySelector<HTMLButtonElement>('.reference-option')!;
    const checkFocus = spyOn(component, 'checkFocus').and.callThrough();

    option.dispatchEvent(new FocusEvent('focusout', { bubbles: true, relatedTarget: outside }));
    fixture.detectChanges();

    expect(checkFocus).toHaveBeenCalled();
    expect(checkFocus.calls.mostRecent().args[0].relatedTarget).toBe(outside);
    expect(component.opened()).toBeFalse();
    expect(overlay.querySelector('.reference-panel')).toBeNull();
  });

  it('fecha com Escape no campo ou no painel e devolve o foco ao campo', () => {
    const field = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    openPanel();
    field.focus();
    field.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fixture.detectChanges();
    expect(component.opened()).toBeFalse();

    openPanel();
    const option = overlay.querySelector<HTMLButtonElement>('.reference-option')!;
    option.focus();
    option.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }));
    fixture.detectChanges();

    expect(component.opened()).toBeFalse();
    expect(document.activeElement).toBe(field);
  });

  it('fecha após clique externo do ponteiro', () => {
    openPanel();
    outside.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true, pointerType: 'mouse' }));
    outside.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
    outside.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));
    outside.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    fixture.detectChanges();

    expect(component.opened()).toBeFalse();
    expect(overlay.querySelector('.reference-panel')).toBeNull();
  });

  it('navega pelas opções com setas e conserva o painel aberto', fakeAsync(() => {
    const field = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    field.focus();
    field.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true, cancelable: true }));
    fixture.detectChanges();
    tick();
    const options = overlay.querySelectorAll<HTMLButtonElement>('.reference-option');
    expect(document.activeElement).toBe(options[0]);

    options[0].dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true, cancelable: true }));
    expect(document.activeElement).toBe(options[1]);
    options[1].dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowUp', bubbles: true, cancelable: true }));

    expect(document.activeElement).toBe(options[0]);
    expect(component.opened()).toBeTrue();
  }));

  it('seleciona a opção focada com Enter', fakeAsync(() => {
    const selected = spyOn(component.selected, 'emit');
    const field = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    field.focus();
    field.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true, cancelable: true }));
    fixture.detectChanges();
    tick();
    const option = overlay.querySelector<HTMLButtonElement>('.reference-option')!;
    expect(document.activeElement).toBe(option);

    option.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }));
    fixture.detectChanges();

    expect(selected).toHaveBeenCalledWith('7');
    expect(component.opened()).toBeFalse();
    expect(document.activeElement).toBe(field);
  }));

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

  function openPanel(): void {
    component.opened.set(true);
    fixture.detectChanges();
  }
});
