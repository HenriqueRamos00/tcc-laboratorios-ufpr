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
import { DashboardTecnicoComponent } from '@/app/pages/tecnico/dashboard/dashboard-tecnico.component';
import { OrcamentosComponent } from '@/app/pages/admin/orcamentos/orcamentos.component';
import { OrcamentosTecnicosComponent } from '@/app/pages/tecnico/orcamentos/orcamentos.component';
import { EnsaiosComponent } from '@/app/pages/tecnico/ensaios/ensaios.component';
import { EnsaioFormComponent } from '@/app/pages/tecnico/ensaios/ensaio-form.component';
import { EnsaioStatusComponent } from '@/app/pages/tecnico/ensaios/ensaio-status.component';
import { RelatoriosComponent } from '@/app/pages/tecnico/relatorios/relatorios.component';
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
    const status = filhos.find((rota) => rota.path === 'ensaios/:id/status');
    const editar = filhos.find((rota) => rota.path === 'ensaios/:id/editar');

    expect(tecnico?.data?.['role']).toBe('TECNICO');
    expect(await lista?.loadComponent?.()).toBe(EnsaiosComponent);
    expect(await novo?.loadComponent?.()).toBe(EnsaioFormComponent);
    expect(await status?.loadComponent?.()).toBe(EnsaioStatusComponent);
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

  it('mantém Relatórios como rota canônica e redireciona os endereços antigos', async () => {
    const filhos = rotaTecnico?.children ?? [];
    const resultados = filhos.find((rota) => rota.path === 'resultados');
    const relatorios = filhos.find((rota) => rota.path === 'relatorios');
    const resultadoPorId = filhos.find((rota) => rota.path === 'resultados/:id');
    const relatorioPorId = filhos.find((rota) => rota.path === 'relatorios/:id');

    expect(rotaTecnico?.data?.['role']).toBe('TECNICO');
    expect(await relatorios?.loadComponent?.()).toBe(RelatoriosComponent);
    expect(await relatorioPorId?.loadComponent?.()).toBe(RelatoriosComponent);
    expect(resultados?.pathMatch).toBe('full');
    expect(resultadoPorId?.pathMatch).toBe('full');
    expect(typeof resultados?.redirectTo).toBe('function');
    expect(typeof resultadoPorId?.redirectTo).toBe('function');

    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    const router = TestBed.inject(Router);
    const listRedirect = resultados?.redirectTo;
    const detailRedirect = resultadoPorId?.redirectTo;
    expect(typeof listRedirect).toBe('function');
    expect(typeof detailRedirect).toBe('function');

    const listUrl = TestBed.runInInjectionContext(() =>
      (listRedirect as (snapshot: ActivatedRouteSnapshot) => UrlTree)({
        params: {},
        queryParams: { filtro: 'pendente' },
        fragment: 'lista',
      } as unknown as ActivatedRouteSnapshot),
    );
    const detailUrl = TestBed.runInInjectionContext(() =>
      (detailRedirect as (snapshot: ActivatedRouteSnapshot) => UrlTree)({
        params: { id: '804' },
        queryParams: { aba: 'pdf' },
        fragment: 'documento',
      } as unknown as ActivatedRouteSnapshot),
    );

    expect(router.serializeUrl(listUrl)).toBe('/tecnico/relatorios?filtro=pendente#lista');
    expect(router.serializeUrl(detailUrl)).toBe('/tecnico/relatorios/804?aba=pdf#documento');
    expect(filhos.find((rota) => rota.path === 'ensaios')).toBeDefined();
    expect(filhos.find((rota) => rota.path === 'ensaios-amostras')).toBeUndefined();
  });

  it('carrega o dashboard técnico protegido na rota raiz', async () => {
    const dashboardTecnico = rotaTecnico?.children?.find(
      (rota) => rota.path === '',
    );

    expect(rotaTecnico?.data?.['role']).toBe('TECNICO');
    expect(rotaTecnico?.canActivate).toContain(authGuard);
    expect(dashboardTecnico?.title).toBe('Dashboard - Portal Interno Lactec');
    expect(await dashboardTecnico?.loadComponent?.()).toBe(DashboardTecnicoComponent);
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
