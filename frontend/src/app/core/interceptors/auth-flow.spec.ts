import { HttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';

import { appConfig } from '@/app/app.config';
import { API_URL } from '@/app/environment/env';
import { clientIdentity, clientLoginResponse, clientToken } from '@/app/model/client-session.spec-util';
import { Token } from '@/app/model/token';
import { LoginService, LS_TOKEN, tokenGetter } from '@/app/services/login.service';

describe('Sessão com os interceptadores configurados na aplicação', () => {
  let http: HttpTestingController;
  let client: HttpClient;
  let login: LoginService;
  let navigate: jasmine.Spy;

  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [...appConfig.providers, provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    client = TestBed.inject(HttpClient);
    login = TestBed.inject(LoginService);
    navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
  });

  afterEach(() => {
    http.verify();
    login.logout();
  });

  for (const source of ['backend-lab', 'lactec'] as const) {
    for (const keepConnected of [true, false]) {
      it(`envia o JWT ${source} de ${keepConnected ? 'localStorage' : 'sessionStorage'} e preserva a sessão em 403`, () => {
        const response = clientLoginResponse(source);
        login.login({ email: response.user.email, password: 'senha' }, keepConnected).subscribe();
        const request = http.expectOne(`${API_URL}/auth/login`);
        expect(request.request.headers.has('Authorization')).toBeFalse();
        request.flush(response);

        client.get(`${API_URL}/api/quotes/sem-acesso`).subscribe({ error: () => {} });
        const forbidden = http.expectOne(`${API_URL}/api/quotes/sem-acesso`);
        expect(forbidden.request.headers.get('Authorization')).toBe(`Bearer ${response.Token}`);
        forbidden.flush({}, { status: 403, statusText: 'Forbidden' });
        expect(tokenGetter()).toBe(response.Token);
        expect(navigate).not.toHaveBeenCalled();
      });
    }
  }

  it('remove a sessão da aba e redireciona quando a API recusa seu JWT com 401', () => {
    const response = clientLoginResponse();
    login.login({ email: response.user.email, password: 'senha' }, false).subscribe();
    http.expectOne(`${API_URL}/auth/login`).flush(response);
    client.get(`${API_URL}/api/quotes`).subscribe({ error: () => {} });
    http.expectOne(`${API_URL}/api/quotes`).flush({}, { status: 401, statusText: 'Unauthorized' });
    http.expectOne(`${API_URL}/auth/refresh`).flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(sessionStorage.getItem(LS_TOKEN)).toBeNull();
    expect(login.currentIdentity()).toBeNull();
    expect(navigate).toHaveBeenCalledOnceWith(['/login']);
  });

  it('um 401 de uma sessão persistente antiga não apaga o novo login mantido na aba', () => {
    const first = clientLoginResponse('lactec');
    login.login({ email: first.user.email, password: 'senha' }).subscribe();
    http.expectOne(`${API_URL}/auth/login`).flush(first);
    client.get(`${API_URL}/api/quotes`).subscribe({ error: () => {} });
    const oldRequest = http.expectOne(`${API_URL}/api/quotes`);

    const next = clientLoginResponse('backend-lab');
    login.login({ email: next.user.email, password: 'senha' }, false).subscribe();
    http.expectOne(`${API_URL}/auth/login`).flush(next);
    oldRequest.flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(tokenGetter()).toBe(next.Token);
    expect(sessionStorage.getItem(LS_TOKEN)).toBe(next.Token);
    expect(navigate).not.toHaveBeenCalled();
  });
  function sessionWith(exp: number): Token {
    return { Token: clientToken('lactec', { exp, jti: `${exp}-${Math.random()}` }), tokenType: 'Bearer',
      expiresIn: 900, user: clientIdentity('lactec') };
  }

  function loginWith(response: Token): void {
    login.login({ email: response.user.email, password: 'senha' }).subscribe();
    http.expectOne(`${API_URL}/auth/login`).flush(response);
  }

  const now = () => Math.floor(Date.now() / 1000);

  it('renova antes de enviar quando o token está para vencer e usa o novo token', () => {
    const old = sessionWith(now() + 30);
    loginWith(old);
    const renewed = sessionWith(now() + 900);

    client.get(`${API_URL}/api/quotes`).subscribe();
    const refresh = http.expectOne(`${API_URL}/auth/refresh`);
    expect(refresh.request.method).toBe('POST');
    expect(refresh.request.headers.get('Authorization')).toBe(`Bearer ${old.Token}`);
    refresh.flush(renewed);

    const request = http.expectOne(`${API_URL}/api/quotes`);
    expect(request.request.headers.get('Authorization')).toBe(`Bearer ${renewed.Token}`);
    request.flush([]);
    expect(localStorage.getItem(LS_TOKEN)).toBe(renewed.Token);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('pedidos simultâneos com o token vencendo fazem uma única renovação', () => {
    loginWith(sessionWith(now() + 30));
    const renewed = sessionWith(now() + 900);

    client.get(`${API_URL}/api/quotes`).subscribe();
    client.get(`${API_URL}/api/equipments`).subscribe();
    http.expectOne(`${API_URL}/auth/refresh`).flush(renewed);

    for (const path of ['/api/quotes', '/api/equipments']) {
      const request = http.expectOne(`${API_URL}${path}`);
      expect(request.request.headers.get('Authorization')).toBe(`Bearer ${renewed.Token}`);
      request.flush([]);
    }
  });

  it('um 401 com token ainda válido renova uma vez e repete o pedido', () => {
    const old = sessionWith(now() + 900);
    loginWith(old);
    const renewed = sessionWith(now() + 900);
    let answer: unknown;

    client.get(`${API_URL}/api/quotes`).subscribe(body => answer = body);
    http.expectOne(`${API_URL}/api/quotes`).flush({}, { status: 401, statusText: 'Unauthorized' });
    http.expectOne(`${API_URL}/auth/refresh`).flush(renewed);
    const retry = http.expectOne(`${API_URL}/api/quotes`);
    expect(retry.request.headers.get('Authorization')).toBe(`Bearer ${renewed.Token}`);
    retry.flush(['ok']);

    expect(answer).toEqual(['ok']);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('sai da sessão quando a renovação é recusada', () => {
    loginWith(sessionWith(now() + 900));

    client.get(`${API_URL}/api/quotes`).subscribe({ error: () => {} });
    http.expectOne(`${API_URL}/api/quotes`).flush({}, { status: 401, statusText: 'Unauthorized' });
    http.expectOne(`${API_URL}/auth/refresh`).flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(tokenGetter()).toBeNull();
    expect(navigate).toHaveBeenCalledOnceWith(['/login']);
  });

  it('mantém a sessão quando a renovação falha por indisponibilidade da API', () => {
    const session = sessionWith(now() + 900);
    loginWith(session);
    let failure: unknown;

    client.get(`${API_URL}/api/quotes`).subscribe({ error: error => failure = error });
    http.expectOne(`${API_URL}/api/quotes`).flush({}, { status: 401, statusText: 'Unauthorized' });
    http.expectOne(`${API_URL}/auth/refresh`).error(new ProgressEvent('error'), { status: 0 });

    expect((failure as { status?: number })?.status).toBe(401);
    expect(tokenGetter()).toBe(session.Token);
    expect(navigate).not.toHaveBeenCalled();
    http.expectNone(`${API_URL}/api/quotes`);
  });

  it('sai da sessão quando o pedido repetido após renovar também recebe 401', () => {
    loginWith(sessionWith(now() + 900));

    client.get(`${API_URL}/api/quotes`).subscribe({ error: () => {} });
    http.expectOne(`${API_URL}/api/quotes`).flush({}, { status: 401, statusText: 'Unauthorized' });
    http.expectOne(`${API_URL}/auth/refresh`).flush(sessionWith(now() + 900));
    http.expectOne(`${API_URL}/api/quotes`).flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(tokenGetter()).toBeNull();
    expect(navigate).toHaveBeenCalledOnceWith(['/login']);
    http.expectNone(`${API_URL}/auth/refresh`);
  });

  it('ao voltar com um token lactec vencido, restaura a sessão renovando', () => {
    const response = sessionWith(now() - 600);
    localStorage.setItem(LS_TOKEN, response.Token);
    const renewed = sessionWith(now() + 900);
    let restored: boolean | undefined;

    login.restoreSession().subscribe(value => restored = value);
    http.expectOne(`${API_URL}/auth/refresh`).flush(renewed);

    expect(restored).toBeTrue();
    expect(tokenGetter()).toBe(renewed.Token);
    expect(login.currentIdentity()?.id).toBe(renewed.user.id);
  });

  it('um token de outra origem vencido não tenta renovar', () => {
    localStorage.setItem(LS_TOKEN, clientToken('backend-lab', { exp: now() - 600 }));
    let restored: boolean | undefined;

    login.restoreSession().subscribe(value => restored = value);

    http.expectNone(`${API_URL}/auth/refresh`);
    expect(restored).toBeFalse();
    expect(tokenGetter()).toBeNull();
  });
});
