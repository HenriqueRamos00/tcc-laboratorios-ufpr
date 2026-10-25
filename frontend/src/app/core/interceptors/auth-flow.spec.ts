import { HttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';

import { appConfig } from '@/app/app.config';
import { API_URL } from '@/app/environment/env';
import { clientLoginResponse } from '@/app/model/client-session.spec-util';
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
});
