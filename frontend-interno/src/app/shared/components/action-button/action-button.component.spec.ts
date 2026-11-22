import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { ActionButtonComponent } from './action-button.component';

describe('Botão de ação compartilhado', () => {
  let fixture: ComponentFixture<ActionButtonComponent>;
  let component: ActionButtonComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ActionButtonComponent],
      providers: [provideRouter([])],
    }).compileComponents();
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

  it('mantém o tipo submit quando não recebe destino de navegação', () => {
    fixture.componentRef.setInput('type', 'submit');
    fixture.detectChanges();

    expect((fixture.nativeElement.querySelector('button') as HTMLButtonElement).type).toBe('submit');
  });

  it('renderiza um link com endereço navegável', () => {
    fixture.componentRef.setInput('routerLink', '/tecnico/ensaios');
    fixture.detectChanges();

    const link = fixture.nativeElement.querySelector('a') as HTMLAnchorElement;

    expect(link).not.toBeNull();
    expect(link.getAttribute('href')).toBe('/tecnico/ensaios');
    expect(fixture.nativeElement.querySelector('button')).toBeNull();
  });

  it('preserva comandos, parâmetros e nome acessível do link', () => {
    fixture.componentRef.setInput('routerLink', ['/tecnico/ensaios', 'novo']);
    fixture.componentRef.setInput('queryParams', { quoteId: 42, quoteCode: 'ORC-42' });
    fixture.componentRef.setInput('ariaLabel', 'Novo ensaio para orçamento ORC-42');
    fixture.detectChanges();

    const link = fixture.nativeElement.querySelector('a') as HTMLAnchorElement;

    expect(link.getAttribute('href')).toBe('/tecnico/ensaios/novo?quoteId=42&quoteCode=ORC-42');
    expect(link.getAttribute('aria-label')).toBe('Novo ensaio para orçamento ORC-42');
  });

  it('remove o destino e a navegação quando o link está desabilitado ou carregando', () => {
    const router = TestBed.inject(Router);
    const activated = jasmine.createSpy('activated');
    component.activated.subscribe(activated);
    fixture.componentRef.setInput('routerLink', '/tecnico/ensaios');

    for (const state of ['disabled', 'loading'] as const) {
      fixture.componentRef.setInput('disabled', state === 'disabled');
      fixture.componentRef.setInput('loading', state === 'loading');
      fixture.detectChanges();

      const link = fixture.nativeElement.querySelector('a') as HTMLAnchorElement;
      expect(link.hasAttribute('href')).toBeFalse();
      expect(link.getAttribute('aria-disabled')).toBe('true');
      expect(link.getAttribute('tabindex')).toBe('-1');

      link.click();
      expect(router.url).toBe('/');
    }

    expect(activated).not.toHaveBeenCalled();
  });

  it('permite selecionar o estilo secundário sem alterar a ação', () => {
    fixture.componentRef.setInput('variant', 'secondary');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('button.mat-mdc-outlined-button')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.action-button__label')?.textContent?.trim()).toBe('Preparar relatório');
  });

  it('mantém a variante textual com hierarquia visual baixa', () => {
    fixture.componentRef.setInput('variant', 'text');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('button.mat-mdc-button')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('button.mat-mdc-unelevated-button')).toBeNull();
  });
});
