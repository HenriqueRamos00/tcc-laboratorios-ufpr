import { BreakpointObserver } from '@angular/cdk/layout';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { LoginService } from '@/app/services/login.service';
import { TourService } from '@/app/services/tour.service';
import { UserRole } from '@core/store/user-role.store';
import { ate, balao, mostrando, tituloDoBalao } from '@/app/services/tour.spec-util';

import { ShellComponent } from './shell.component';
import { menuTour, pageTourFor } from './shell.tour';

function montarShell(role: 'ADMIN' | 'TECNICO' = 'ADMIN') {
  const logout = jasmine.createSpy('logout');
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      {
        provide: BreakpointObserver,
        useValue: { observe: () => of({ matches: false, breakpoints: {} }) },
      },
      { provide: LoginService, useValue: { logout } },
      { provide: UserRole, useValue: { role: () => role } },
    ],
  });

  const fixture = TestBed.createComponent(ShellComponent);
  fixture.detectChanges();
  return { fixture, logout, tour: TestBed.inject(TourService) };
}

function botaoDoMenu(fixture: ComponentFixture<ShellComponent>, alvo: string): HTMLButtonElement {
  const button = (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>(
    `[data-tour="${alvo}"]`,
  );
  if (!button) throw new Error(`Botão de navegação ausente: ${alvo}`);
  return button;
}

function opcoesDaAjuda(): HTMLButtonElement[] {
  return Array.from(document.querySelectorAll<HTMLButtonElement>('#shell-help-options button'));
}

describe('ShellComponent, ajuda guiada', () => {
  let tour: TourService | undefined;

  afterEach(async () => {
    tour?.stop();
    await ate(() => balao() === null, 'balão removido ao encerrar');
  });

  it('abre as opções Sobre o menu e Sobre esta página', () => {
    const shell = montarShell();
    tour = shell.tour;

    const ajuda = botaoDoMenu(shell.fixture, 'menu-ajuda');
    expect(ajuda.getAttribute('aria-expanded')).toBe('false');
    ajuda.click();
    shell.fixture.detectChanges();

    const [sobreMenu, sobrePagina] = opcoesDaAjuda();
    expect(ajuda.getAttribute('aria-expanded')).toBe('true');
    expect(sobreMenu.textContent).toContain('Sobre o menu');
    expect(sobrePagina.textContent).toContain('Sobre esta página');
    expect(sobrePagina.disabled).toBeTrue();
  });

  it('inicia a apresentação do menu com os itens do perfil atual', async () => {
    const shell = montarShell('TECNICO');
    tour = shell.tour;

    botaoDoMenu(shell.fixture, 'menu-ajuda').click();
    shell.fixture.detectChanges();
    opcoesDaAjuda()[0].click();

    await mostrando('Dashboard');
    expect(tituloDoBalao()).toBe('Dashboard');
    expect(shell.fixture.nativeElement.querySelector('[data-tour="menu-dashboard"]')
      .classList).toContain('driver-active-element');
    expect(shell.fixture.nativeElement.querySelector('[data-tour="menu-clientes"]')).toBeNull();
    expect(menuTour('ADMIN').steps.some((step) => step.target === 'menu-clientes')).toBeTrue();
  });

  it('oferece orientação própria nas telas atendidas e desativa em rotas sem roteiro', () => {
    const orcamentos = pageTourFor('/admin/orcamentos?status=ALL');
    expect(orcamentos?.steps.map((step) => step.target)).toContain('pagina-paginacao');
    expect(pageTourFor('/login')).toBeNull();
  });

  it('explica em etapas os campos e as ações do formulário de ensaio', () => {
    const roteiro = pageTourFor('/tecnico/ensaios/novo');
    expect(roteiro?.steps.map((step) => step.target)).toEqual([
      'ensaio-form-orcamento',
      'ensaio-form-amostra',
      'ensaio-form-tipo',
      'ensaio-form-formato',
      'ensaio-form-metodo-norma',
      'ensaio-form-agendamento',
      'ensaio-form-responsavel',
      'ensaio-form-instrumentos',
      'ensaio-form-classificacao',
      'ensaio-form-medicoes',
      'ensaio-form-rascunho',
      'ensaio-form-acoes',
    ]);
    expect(pageTourFor('/tecnico/ensaios/42/editar')?.steps.length).toBe(12);
  });
});
