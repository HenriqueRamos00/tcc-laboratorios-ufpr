import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActionButtonComponent } from './action-button.component';

describe('Botão de ação compartilhado', () => {
  let fixture: ComponentFixture<ActionButtonComponent>;
  let component: ActionButtonComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [ActionButtonComponent] }).compileComponents();
    fixture = TestBed.createComponent(ActionButtonComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('label', 'Preparar relatório');
    fixture.componentRef.setInput('icon', 'description');
    fixture.detectChanges();
  });

  it('mantém ícone e texto dentro de um mesmo grupo alinhável', () => {
    const content = fixture.nativeElement.querySelector('.action-button__content') as HTMLElement;

    expect(content.querySelector('mat-icon')?.textContent?.trim()).toBe('description');
    expect(content.querySelector('.action-button__label')?.textContent?.trim()).toBe('Preparar relatório');
  });

  it('emite a ação ao clicar no botão habilitado', () => {
    const activated = jasmine.createSpy('activated');
    component.activated.subscribe(activated);

    (fixture.nativeElement.querySelector('button') as HTMLButtonElement).click();

    expect(activated).toHaveBeenCalledTimes(1);
  });

  it('desabilita a ação e mostra o indicador durante o carregamento', () => {
    fixture.componentRef.setInput('loading', true);
    fixture.detectChanges();
    const button = fixture.nativeElement.querySelector('button') as HTMLButtonElement;

    expect(button.disabled).toBeTrue();
    expect(fixture.nativeElement.querySelector('mat-spinner')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('mat-icon')).toBeNull();
  });

  it('permite selecionar o estilo secundário sem alterar a ação', () => {
    fixture.componentRef.setInput('variant', 'secondary');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('button.mat-mdc-outlined-button')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.action-button__label')?.textContent?.trim()).toBe('Preparar relatório');
  });
});
