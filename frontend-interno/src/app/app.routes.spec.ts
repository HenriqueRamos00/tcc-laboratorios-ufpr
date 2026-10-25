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
import { PlaceholderComponent } from '@/app/pages/placeholder/placeholder.component';
import { LoginService } from '@/app/services/login.service';
import { routes } from './app.routes';

describe('Rotas do Portal Interno', () => {
  const rotaAdmin = routes.find((rota) => rota.path === 'admin');
  const rotaDashboard = rotaAdmin?.children?.find((rota) => rota.path === '');

  it('carrega o dashboard do administrador na rota raiz de administração', async () => {
    expect(rotaAdmin?.data?.['role']).toBe('ADMIN');
    expect(rotaAdmin?.canActivate).toContain(authGuard);
    expect(await rotaDashboard?.loadComponent?.()).toBe(DashboardAdministradorComponent);
  });

  it('mantém o dashboard técnico como página provisória', async () => {
    const rotaTecnico = routes.find((rota) => rota.path === 'tecnico');
    const dashboardTecnico = rotaTecnico?.children?.find((rota) => rota.path === '');

    expect(rotaTecnico?.data?.['role']).toBe('TECNICO');
    expect(await dashboardTecnico?.loadComponent?.()).toBe(PlaceholderComponent);
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
    const rota = { data: { role: 'ADMIN' } } as unknown as ActivatedRouteSnapshot;
    const resultado = TestBed.runInInjectionContext(() =>
      authGuard(rota, { url: '/admin' } as RouterStateSnapshot),
    );

    expect(resultado).toEqual(jasmine.any(UrlTree));
    expect(router.serializeUrl(resultado as UrlTree)).toBe('/tecnico');
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
    const rota = { data: { role: 'ADMIN' } } as unknown as ActivatedRouteSnapshot;
    const resultado = TestBed.runInInjectionContext(() =>
      authGuard(rota, { url: '/admin' } as RouterStateSnapshot),
    );

    expect(resultado).toEqual(jasmine.any(UrlTree));
    expect(router.serializeUrl(resultado as UrlTree)).toBe('/login');
    expect(loginService.logout).toHaveBeenCalled();
  });
});
