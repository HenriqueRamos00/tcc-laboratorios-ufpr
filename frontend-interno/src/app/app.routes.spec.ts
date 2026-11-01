import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  provideRouter,
  Router,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';

import { authGuard } from '@core/guards/auth.guard';
import { UserRole } from '@core/store/user-role.store';
import { DashboardAdministradorComponent } from '@/app/pages/admin/dashboard/dashboard-administrador.component';
import { OrcamentosComponent } from '@/app/pages/admin/orcamentos/orcamentos.component';
import { OrcamentosTecnicosComponent } from '@/app/pages/tecnico/orcamentos/orcamentos.component';
import { PlaceholderComponent } from '@/app/pages/placeholder/placeholder.component';
import { EnsaiosComponent } from '@/app/pages/tecnico/ensaios/ensaios.component';
import { EnsaioFormComponent } from '@/app/pages/tecnico/ensaios/ensaio-form.component';
import { LoginService } from '@/app/services/login.service';
import { routes } from './app.routes';

describe('Rotas do Portal Interno', () => {
  const rotaAdmin = routes.find((rota) => rota.path === 'admin');
  const rotaDashboard = rotaAdmin?.children?.find((rota) => rota.path === '');
  const rotaOrcamentosAdmin = rotaAdmin?.children?.find((rota) => rota.path === 'orcamentos');
  const rotaTecnico = routes.find((rota) => rota.path === 'tecnico');
  const rotaOrcamentosTecnicos = rotaTecnico?.children?.find((rota) => rota.path === 'orcamentos');

  it('carrega o dashboard do administrador na rota raiz de administração', async () => {
    expect(rotaAdmin?.data?.['role']).toBe('ADMIN');
    expect(rotaAdmin?.canActivate).toContain(authGuard);
    expect(await rotaDashboard?.loadComponent?.()).toBe(
      DashboardAdministradorComponent,
    );
  });

  it('protege a manutenção de ensaios pelo perfil TECNICO e carrega as rotas da HU25', async () => {
    const tecnico = routes.find((rota) => rota.path === 'tecnico');
    const filhos = tecnico?.children ?? [];
    const lista = filhos.find((rota) => rota.path === 'ensaios');
    const novo = filhos.find((rota) => rota.path === 'ensaios/novo');
    const editar = filhos.find((rota) => rota.path === 'ensaios/:id/editar');

    expect(tecnico?.data?.['role']).toBe('TECNICO');
    expect(await lista?.loadComponent?.()).toBe(EnsaiosComponent);
    expect(await novo?.loadComponent?.()).toBe(EnsaioFormComponent);
    expect(await editar?.loadComponent?.()).toBe(EnsaioFormComponent);
    expect(novo?.canDeactivate).toBeDefined();
    expect(editar?.canDeactivate).toBeDefined();
  });

  it('preserva a tela administrativa de orçamentos na rota do administrador', async () => {
    expect(rotaAdmin?.data?.['role']).toBe('ADMIN');
    expect(await rotaOrcamentosAdmin?.loadComponent?.()).toBe(OrcamentosComponent);
  });

  it('carrega a consulta de orçamentos com acesso exclusivo de técnico', async () => {
    expect(rotaTecnico?.data?.['role']).toBe('TECNICO');
    expect(rotaTecnico?.canActivate).toContain(authGuard);
    expect(await rotaOrcamentosTecnicos?.loadComponent?.()).toBe(OrcamentosTecnicosComponent);
  });

  it('mantém o dashboard técnico como página provisória', async () => {
    const rotaTecnico = routes.find((rota) => rota.path === 'tecnico');
    const dashboardTecnico = rotaTecnico?.children?.find(
      (rota) => rota.path === '',
    );

    expect(rotaTecnico?.data?.['role']).toBe('TECNICO');
    expect(await dashboardTecnico?.loadComponent?.()).toBe(
      PlaceholderComponent,
    );
  });

  it('redireciona para o painel do perfil atual quando o perfil não é ADMIN', () => {
    const loginService = {
      hasValidSession: () => true,
      logout: jasmine.createSpy('logout'),
    };
    const userRole = {
      role: () => 'TECNICO',
      dashboardPath: () => '/tecnico',
    };
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        { provide: LoginService, useValue: loginService },
        { provide: UserRole, useValue: userRole },
      ],
    });
    const router = TestBed.inject(Router);
    const rota = {
      data: { role: 'ADMIN' },
    } as unknown as ActivatedRouteSnapshot;
    const resultado = TestBed.runInInjectionContext(() =>
      authGuard(rota, { url: '/admin' } as RouterStateSnapshot),
    );

    expect(resultado).toEqual(jasmine.any(UrlTree));
    expect(router.serializeUrl(resultado as UrlTree)).toBe('/tecnico');
  });

  it('redireciona um ADMIN para seu painel ao tentar abrir a consulta exclusiva do técnico', () => {
    const loginService = {
      hasValidSession: () => true,
      logout: jasmine.createSpy('logout'),
    };
    const userRole = {
      role: () => 'ADMIN',
      dashboardPath: () => '/admin',
    };
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        { provide: LoginService, useValue: loginService },
        { provide: UserRole, useValue: userRole },
      ],
    });
    const router = TestBed.inject(Router);
    const resultado = TestBed.runInInjectionContext(() =>
      authGuard(
        { data: { role: 'TECNICO' } } as unknown as ActivatedRouteSnapshot,
        { url: '/tecnico/orcamentos' } as RouterStateSnapshot,
      ),
    );

    expect(resultado).toEqual(jasmine.any(UrlTree));
    expect(router.serializeUrl(resultado as UrlTree)).toBe('/admin');
  });

  it('redireciona uma sessão ausente para o login', () => {
    const loginService = {
      hasValidSession: () => false,
      logout: jasmine.createSpy('logout'),
    };
    const userRole = {
      role: () => null,
      dashboardPath: () => '/login',
    };
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        { provide: LoginService, useValue: loginService },
        { provide: UserRole, useValue: userRole },
      ],
    });
    const router = TestBed.inject(Router);
    const rota = {
      data: { role: 'ADMIN' },
    } as unknown as ActivatedRouteSnapshot;
    const resultado = TestBed.runInInjectionContext(() =>
      authGuard(rota, { url: '/admin' } as RouterStateSnapshot),
    );

    expect(resultado).toEqual(jasmine.any(UrlTree));
    expect(router.serializeUrl(resultado as UrlTree)).toBe('/login');
    expect(loginService.logout).toHaveBeenCalled();
  });
});
