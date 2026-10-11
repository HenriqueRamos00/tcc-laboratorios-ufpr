import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { AUTH_API_URL } from '@/app/environment/env';
import { LoginService, LS_SESSION } from './login.service';

describe('Serviço de autenticação', () => {
  let service: LoginService;
  let http: HttpTestingController;

  beforeEach(() => {
    window.localStorage.clear();
    window.sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(LoginService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    window.localStorage.clear();
    window.sessionStorage.clear();
  });

  it('restaura o cliente de demonstração da branch AutoLAB sem chamar a API', () => {
    service.login({ email: ' LACTEC@LACTEC ', password: 'lactec' }).subscribe();

    http.expectNone(`${AUTH_API_URL}/auth/login`);
    expect(service.hasValidSession()).toBeTrue();
    expect(service.currentUser()).toBe('Cliente Lactec');
    expect(service.currentProfile()?.email).toBe('lactec@lactec');
    expect(service.currentProfile()?.role).toBe('Client');
    expect(service.authorizationHeader()).toContain('Bearer ');
    const stored = JSON.parse(window.localStorage.getItem(LS_SESSION) ?? '{}');
    expect(stored.user.email).toBe('lactec@lactec');
    expect(stored.expiresIn).toBe(86400);
  });

  it('respeita a opção de conexão e o logout no login de demonstração', () => {
    service.login({ email: 'lactec@lactec', password: 'lactec' }, false).subscribe();

    http.expectNone(`${AUTH_API_URL}/auth/login`);
    expect(window.localStorage.getItem(LS_SESSION)).toBeNull();
    expect(JSON.parse(window.sessionStorage.getItem(LS_SESSION) ?? '{}').user.email)
      .toBe('lactec@lactec');
    service.logout();
    expect(window.sessionStorage.getItem(LS_SESSION)).toBeNull();
    expect(service.hasValidSession()).toBeFalse();
  });

  it('não aceita a conta de demonstração com senha incorreta', () => {
    service.login({ email: 'lactec@lactec', password: 'incorreta' })
      .subscribe({ error: () => undefined });
    http.expectOne(`${AUTH_API_URL}/auth/login`).flush({}, {
      status: 401, statusText: 'Não autorizado',
    });

    expect(service.hasValidSession()).toBeFalse();
    expect(window.localStorage.getItem(LS_SESSION)).toBeNull();
  });

  it('envia credenciais e preserva o perfil retornado pela API', () => {
    const profile = {
      id: '42',
      name: 'Cliente Real',
      email: 'cliente@empresa.com',
      role: 'Client' as const,
      companies: [{ id: '7', name: 'Empresa vinculada' }],
    };
    let returnedProfile: unknown;

    service.login({ email: profile.email, password: 'senha' }).subscribe((response) => {
      returnedProfile = response.user;
    });

    const request = http.expectOne(`${AUTH_API_URL}/auth/login`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ email: profile.email, password: 'senha' });
    request.flush({
      accessToken: 'opaque-token',
      tokenType: 'Bearer',
      expiresIn: 3600,
      user: profile,
    });

    expect(returnedProfile).toEqual(profile);
    expect(service.currentProfile()).toEqual(profile);
    expect(JSON.parse(window.localStorage.getItem(LS_SESSION) ?? '{}').user).toEqual(profile);
  });

  it('remove chaves de login antigas e sessões incompatíveis', () => {
    TestBed.resetTestingModule();
    window.localStorage.setItem('lactec.token', 'token-antigo');
    window.sessionStorage.setItem('lactec.username', 'Usuário antigo');
    window.localStorage.setItem(LS_SESSION, JSON.stringify({ token: 'formato-antigo', role: 'client' }));
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    const freshService = TestBed.inject(LoginService);
    http = TestBed.inject(HttpTestingController);

    expect(window.localStorage.getItem('lactec.token')).toBeNull();
    expect(window.sessionStorage.getItem('lactec.username')).toBeNull();
    expect(window.localStorage.getItem(LS_SESSION)).toBeNull();
    expect(freshService.authorizationHeader()).toBeNull();
  });

  it('mantém uma sessão sem persistência quando a opção de conexão está desmarcada', () => {
    service.login({ email: 'cliente@empresa.com', password: 'senha' }, false).subscribe();
    http.expectOne(`${AUTH_API_URL}/auth/login`).flush({
      accessToken: 'opaque-token',
      tokenType: 'Bearer',
      expiresIn: 3600,
      user: {
        id: '42',
        name: 'Cliente Real',
        email: 'cliente@empresa.com',
        role: 'Client',
        companies: [],
      },
    });

    expect(window.localStorage.getItem(LS_SESSION)).toBeNull();
    expect(JSON.parse(window.sessionStorage.getItem(LS_SESSION) ?? '{}').user.name).toBe('Cliente Real');
    expect(service.authorizationHeader()).toBe('Bearer opaque-token');
  });
});
