import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { API_URL } from '@/app/environment/env';
import { LoginService, LS_TOKEN } from '@/app/services/login.service';
import { authTokenInterceptor } from './auth-token.interceptor';
import { authErrorInterceptor } from './auth-error.interceptor';

describe('Interceptors de autorização do portal', () => {
  let client: HttpClient;
  let http: HttpTestingController;
  const logout = jasmine.createSpy('logout');
  beforeEach(() => {
    logout.calls.reset();
    localStorage.setItem(LS_TOKEN, 'portal-token');
    TestBed.configureTestingModule({ providers: [
      provideHttpClient(withInterceptors([authTokenInterceptor, authErrorInterceptor])), provideHttpClientTesting(),
      provideRouter([]), { provide: LoginService, useValue: { logout } },
    ] });
    client = TestBed.inject(HttpClient);
    http = TestBed.inject(HttpTestingController);
    spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
  });
  afterEach(() => { http.verify(); localStorage.clear(); });

  for (const path of ['/api/quotes', '/auth/me', '/api/quotes/q/document'])
    it(`envia Bearer apenas ao portal: ${path}`, () => {
      client.get(API_URL + path).subscribe();
      const request = http.expectOne(API_URL + path);
      expect(request.request.headers.get('Authorization')).toBe('Bearer portal-token');
      request.flush({});
    });

  for (const url of [API_URL + '/auth/login', '/assets/logo.svg', 'https://other.test/api/quotes',
    API_URL + '.evil.test/api/quotes', API_URL + '/api-other'])
    it(`não envia token para ${url}`, () => {
      client.get(url).subscribe();
      const request = http.expectOne(url);
      expect(request.request.headers.has('Authorization')).toBeFalse();
      request.flush({});
    });

  it('preserva autorização explícita', () => {
    client.get(API_URL + '/api/quotes', { headers: { Authorization: 'Bearer explicit' } }).subscribe();
    const request = http.expectOne(API_URL + '/api/quotes');
    expect(request.request.headers.get('Authorization')).toBe('Bearer explicit');
    request.flush({});
  });

  it('um 401 antigo não remove uma sessão de outra conta', () => {
    client.get(API_URL + '/api/quotes').subscribe({ error: () => {} });
    localStorage.setItem(LS_TOKEN, 'new-session-token');
    http.expectOne(API_URL + '/api/quotes').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(logout).not.toHaveBeenCalled();
  });

  for (const [url, status, shouldLogout] of [
    [API_URL + '/api/quotes', 401, true], [API_URL + '/api/quotes', 403, false],
    [API_URL + '/auth/login', 401, false], ['https://other.test/api/quotes', 401, false],
  ] as const) it(`trata ${status} de ${url}`, () => {
    client.get(url).subscribe({ error: () => {} });
    http.expectOne(url).flush({}, { status, statusText: 'Denied' });
    expect(logout).toHaveBeenCalledTimes(shouldLogout ? 1 : 0);
    expect(TestBed.inject(Router).navigate).toHaveBeenCalledTimes(shouldLogout ? 1 : 0);
  });
});
