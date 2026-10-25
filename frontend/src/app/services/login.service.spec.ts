import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { JwtHelperService } from '@auth0/angular-jwt';

import { ROLE_STORAGE_KEY, UserRole } from '@core/store/user-role.store';
import { API_URL } from '@/app/environment/env';
import { clientIdentity, clientLoginResponse, clientToken } from '@/app/model/client-session.spec-util';
import { Token } from '@/app/model/token';
import { authTokenInterceptor } from '@/app/core/interceptors/auth-token.interceptor';
import { LoginService, LS_TOKEN, LS_USERNAME, LS_USER, tokenGetter } from './login.service';

describe('Sessão do Portal do Cliente', () => {
  let service: LoginService;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [
      provideHttpClient(withInterceptors([authTokenInterceptor])), provideHttpClientTesting(),
      { provide: JwtHelperService, useFactory: () => new JwtHelperService() },
    ] });
    service = TestBed.inject(LoginService);
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(UserRole).clear();
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
    sessionStorage.clear();
  });

  function login(source: 'lactec' | 'backend-lab' = 'lactec', keepConnected = true): Token {
    const response = clientLoginResponse(source);
    service.login({ email: '  Marina@Example.COM ', password: '  antiga  ' }, keepConnected).subscribe();
    const request = http.expectOne(`${API_URL}/auth/login`);
    expect(request.request.body).toEqual({ email: 'marina@example.com', password: '  antiga  ' });
    expect(request.request.headers.has('Authorization')).toBeFalse();
    request.flush(response);
    return response;
  }

  for (const source of ['lactec', 'backend-lab'] as const) {
    for (const keepConnected of [true, false]) {
      it(`guarda a sessão ${source} em ${keepConnected ? 'localStorage' : 'sessionStorage'} conforme manter conectado`, () => {
        const response = login(source, keepConnected);
        const storage = keepConnected ? localStorage : sessionStorage;
        const other = keepConnected ? sessionStorage : localStorage;
        expect(storage.getItem(LS_TOKEN)).toBe(response.Token);
        expect(storage.getItem(LS_USERNAME)).toBe(response.user.name);
        expect(JSON.parse(storage.getItem(LS_USER)!)).toEqual(response.user);
        expect(JSON.parse(storage.getItem(ROLE_STORAGE_KEY)!)).toEqual({ id: 2, name: 'client' });
        for (const key of [LS_TOKEN, LS_USERNAME, LS_USER, ROLE_STORAGE_KEY]) expect(other.getItem(key)).toBeNull();
        expect(tokenGetter()).toBe(response.Token);
        expect(service.currentUser()).toBe(response.user.name);
      });
    }
  }

  it('troca o armazenamento e remove os dados da conta anterior nos dois locais', () => {
    const first = login();
    const next = clientLoginResponse('backend-lab');
    next.user.name = 'Outro cliente';
    service.login({ email: next.user.email, password: 'senha' }, false).subscribe();
    http.expectOne(`${API_URL}/auth/login`).flush(next);
    expect(tokenGetter()).not.toBe(first.Token);
    expect(tokenGetter()).toBe(next.Token);
    expect(localStorage.getItem(LS_USER)).toBeNull();
    expect(localStorage.getItem(LS_TOKEN)).toBeNull();
    expect(service.currentUser()).toBe('Outro cliente');
  });

  it('permite o cliente lactec sem empresas autorizadas', () => {
    const response = clientLoginResponse();
    response.user.authorizations!.companyCnpjs = [];
    service.login({ email: response.user.email, password: 'senha' }).subscribe();
    http.expectOne(`${API_URL}/auth/login`).flush(response);
    expect(service.currentIdentity()?.authorizations?.companyCnpjs).toEqual([]);
  });

  for (const [label, change] of [
    ['sem token', (r: Token) => ({ ...r, Token: null })],
    ['com token malformado', (r: Token) => ({ ...r, Token: 'token-do-portal' })],
    ['com token expirado', (r: Token) => ({ ...r, Token: clientToken('lactec', { exp: 1 }) })],
    ['com token interno', (r: Token) => ({ ...r, Token: clientToken('backend-lab', { role: 'ADMIN' }) })],
    ['com fonte desconhecida', (r: Token) => ({ ...r, Token: clientToken('lactec', { authSource: 'outra' }) })],
    ['sem usuário', (r: Token) => ({ ...r, user: null })],
    ['com nome vazio', (r: Token) => ({ ...r, user: { ...r.user, name: ' ' } })],
    ['com perfil interno', (r: Token) => ({ ...r, user: { ...r.user, role: 'ADMIN' } })],
    ['com tipo inválido', (r: Token) => ({ ...r, tokenType: 'Basic' })],
    ['com validade inválida', (r: Token) => ({ ...r, expiresIn: 0 })],
    ['sem autorizações lactec', (r: Token) => ({ ...r, user: { ...r.user, authorizations: null } })],
    ['de outro serviço', (r: Token) => ({ ...r, user: { ...r.user, serviceCode: 'outro' } })],
  ] as const) {
    it(`rejeita resposta ${label} antes de gravar qualquer dado da sessão`, () => {
      let error: unknown;
      service.login({ email: 'marina@example.com', password: 'senha' }).subscribe({ error: e => error = e });
      http.expectOne(`${API_URL}/auth/login`).flush(change(clientLoginResponse()));
      expect(error).toBeTruthy();
      for (const storage of [localStorage, sessionStorage]) {
        for (const key of [LS_TOKEN, LS_USERNAME, LS_USER, ROLE_STORAGE_KEY]) expect(storage.getItem(key)).toBeNull();
      }
      expect(service.currentIdentity()).toBeNull();
    });
  }

  it('uma tentativa recusada não apaga uma sessão válida anterior', () => {
    const response = login('lactec', false);
    service.login({ email: 'outra@example.com', password: 'errada' }).subscribe({ error: () => {} });
    http.expectOne(`${API_URL}/auth/login`).flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(tokenGetter()).toBe(response.Token);
    expect(service.currentUser()).toBe(response.user.name);
  });

  it('uma resposta inválida de novo login preserva a sessão anterior', () => {
    const response = login();
    service.login({ email: 'outra@example.com', password: 'senha' }).subscribe({ error: () => {} });
    http.expectOne(`${API_URL}/auth/login`).flush({ Token: 'inválido', user: null });
    expect(tokenGetter()).toBe(response.Token);
    expect(service.currentUser()).toBe(response.user.name);
  });

  for (const storageName of ['localStorage', 'sessionStorage'] as const) {
    it(`restaura a identidade lactec de ${storageName} por /auth/me e compartilha consultas`, () => {
      const storage = window[storageName];
      const jwt = clientToken();
      storage.setItem(LS_TOKEN, jwt);
      const results: boolean[] = [];
      service.restoreSession().subscribe(value => results.push(value));
      service.restoreSession().subscribe(value => results.push(value));
      const request = http.expectOne(`${API_URL}/auth/me`);
      expect(request.request.headers.get('Authorization')).toBe(`Bearer ${jwt}`);
      request.flush(clientIdentity());
      expect(results).toEqual([true, true]);
      expect(service.currentIdentity()?.authorizations?.companyCnpjs.length).toBe(2);
      expect(storage.getItem(LS_USER)).toBeTruthy();
      service.restoreSession().subscribe(value => expect(value).toBeTrue());
      http.expectNone(`${API_URL}/auth/me`);
    });

    it(`restaura Backend Lab de ${storageName} sem consultar /auth/me`, () => {
      const storage = window[storageName];
      storage.setItem(LS_TOKEN, clientToken('backend-lab'));
      storage.setItem(LS_USERNAME, 'Marina Costa');
      service.restoreSession().subscribe(value => expect(value).toBeTrue());
      expect(service.currentUser()).toBe('Marina Costa');
      expect(TestBed.inject(UserRole).role()?.name).toBe('client');
      http.expectNone(`${API_URL}/auth/me`);
    });
  }

  for (const [label, value] of [
    ['expirado', clientToken('lactec', { exp: 1 })],
    ['malformado', 'invalid.token'],
    ['sem validade', clientToken('backend-lab', { exp: null })],
    ['com papel inválido', clientToken('backend-lab', { role: 123 })],
    ['de administrador', clientToken('backend-lab', { role: 'ADMIN' })],
    ['de técnico', clientToken('backend-lab', { role: 'TECNICO' })],
    ['de fonte desconhecida', clientToken('lactec', { authSource: 'outra' })],
  ]) {
    it(`limpa os dois armazenamentos e o perfil ao restaurar JWT ${label}`, () => {
      for (const storage of [localStorage, sessionStorage]) {
        storage.setItem(LS_TOKEN, value);
        storage.setItem(LS_USERNAME, 'Nome antigo');
        storage.setItem(LS_USER, JSON.stringify(clientIdentity()));
        storage.setItem(ROLE_STORAGE_KEY, JSON.stringify({ id: 2, name: 'client' }));
      }
      service.restoreSession().subscribe(result => expect(result).toBeFalse());
      for (const storage of [localStorage, sessionStorage]) {
        for (const key of [LS_TOKEN, LS_USERNAME, LS_USER, ROLE_STORAGE_KEY]) expect(storage.getItem(key)).toBeNull();
      }
      expect(service.currentIdentity()).toBeNull();
      http.expectNone(`${API_URL}/auth/me`);
    });
  }

  for (const status of [401, 403]) {
    it(`limpa a sessão quando /auth/me retorna ${status}`, () => {
      sessionStorage.setItem(LS_TOKEN, clientToken());
      service.restoreSession().subscribe(result => expect(result).toBeFalse());
      http.expectOne(`${API_URL}/auth/me`).flush({}, { status, statusText: 'Unauthorized' });
      expect(tokenGetter()).toBeNull();
    });
  }

  it('rejeita uma identidade incompatível de /auth/me sem persistir o perfil', () => {
    localStorage.setItem(LS_TOKEN, clientToken());
    service.restoreSession().subscribe(result => expect(result).toBeFalse());
    http.expectOne(`${API_URL}/auth/me`).flush({ ...clientIdentity(), role: 'ADMIN' });
    expect(tokenGetter()).toBeNull();
    expect(service.currentIdentity()).toBeNull();
  });

  it('uma resposta antiga de /auth/me não restaura a sessão após logout', () => {
    localStorage.setItem(LS_TOKEN, clientToken());
    service.restoreSession().subscribe(result => expect(result).toBeFalse());
    const request = http.expectOne(`${API_URL}/auth/me`);
    service.logout();
    request.flush(clientIdentity());
    expect(service.currentUser()).toBeNull();
    expect(tokenGetter()).toBeNull();
  });

  it('uma resposta antiga de /auth/me não sobrescreve outra conta nem seu armazenamento', () => {
    localStorage.setItem(LS_TOKEN, clientToken());
    service.restoreSession().subscribe(result => expect(result).toBeFalse());
    const request = http.expectOne(`${API_URL}/auth/me`);
    const response = login('backend-lab', false);
    request.flush(clientIdentity());
    expect(tokenGetter()).toBe(response.Token);
    expect(localStorage.getItem(LS_USER)).toBeNull();
    expect(service.currentIdentity()?.serviceCode).toBeUndefined();
  });

  it('sair limpa tokens, identidade e perfil dos dois armazenamentos', () => {
    login('lactec', false);
    localStorage.setItem(LS_TOKEN, 'outra-sessao');
    localStorage.setItem(LS_USER, JSON.stringify(clientIdentity()));
    service.logout();
    for (const storage of [localStorage, sessionStorage]) {
      for (const key of [LS_TOKEN, LS_USERNAME, LS_USER, ROLE_STORAGE_KEY]) expect(storage.getItem(key)).toBeNull();
    }
    expect(service.currentUser()).toBeNull();
    expect(TestBed.inject(UserRole).role()).toBeNull();
  });
});
