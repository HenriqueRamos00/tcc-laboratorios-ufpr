import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { JwtHelperService } from '@auth0/angular-jwt';
import { UserRole } from '@core/store/user-role.store';
import { API_URL } from '@/app/environment/env';
import { PortalUser } from '@/app/model/token';
import { authTokenInterceptor } from '@/app/core/interceptors/auth-token.interceptor';
import { LoginService, LS_TOKEN, LS_USERNAME, LS_USER } from './login.service';

const identity: PortalUser = {
  id: '42', name: 'Marina Costa', email: 'marina@example.com', role: 'CLIENTE', serviceCode: 'portal-web',
  authorizations: { companyCnpjs: ['04252011000110', '12ABC34501DE35'] },
};
function token(claims: object): string {
  return [btoa('{"alg":"HS256"}'), btoa(JSON.stringify(claims)), 'signature'].join('.');
}

describe('LoginService, sessão do portal', () => {
  let service: LoginService;
  let http: HttpTestingController;
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [
      provideHttpClient(withInterceptors([authTokenInterceptor])), provideHttpClientTesting(),
      { provide: JwtHelperService, useFactory: () => new JwtHelperService() },
    ] });
    service = TestBed.inject(LoginService);
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(UserRole).clear();
  });
  afterEach(() => { http.verify(); localStorage.clear(); });

  it('normaliza email, preserva senha e guarda a identidade sem decodificar nome do JWT', () => {
    service.login({ email: '  Marina@Example.COM ', password: '  antiga  ' }).subscribe();
    const request = http.expectOne(`${API_URL}/auth/login`);
    expect(request.request.body).toEqual({ email: 'marina@example.com', password: '  antiga  ' });
    expect(request.request.headers.has('Authorization')).toBeFalse();
    request.flush({ Token: 'token-do-portal', tokenType: 'Bearer', expiresIn: 900, user: identity });
    expect(localStorage.getItem(LS_TOKEN)).toBe('token-do-portal');
    expect(localStorage.getItem(LS_USERNAME)).toBe(identity.name);
    expect(JSON.parse(localStorage.getItem(LS_USER)!)).toEqual(identity);
    expect(service.currentUser()).toBe(identity.name);
    expect(TestBed.inject(UserRole).role()?.name).toBe('client');
  });

  it('restaura por /auth/me uma vez e compartilha chamadas simultâneas', () => {
    const jwt = token({ authSource: 'lactec', exp: Date.now() / 1000 + 900 });
    localStorage.setItem(LS_TOKEN, jwt);
    const results: boolean[] = [];
    service.restoreSession().subscribe(value => results.push(value));
    service.restoreSession().subscribe(value => results.push(value));
    const request = http.expectOne(`${API_URL}/auth/me`);
    expect(request.request.headers.get('Authorization')).toBe(`Bearer ${jwt}`);
    request.flush(identity);
    expect(results).toEqual([true, true]);
    expect(service.currentIdentity()?.authorizations?.companyCnpjs.length).toBe(2);
    service.restoreSession().subscribe(value => expect(value).toBeTrue());
    http.expectNone(`${API_URL}/auth/me`);
  });

  it('restaura token Backend Lab sem consultar Backend Auth ou /me', () => {
    localStorage.setItem(LS_TOKEN, token({ role: 'Client', exp: Date.now() / 1000 + 900 }));
    service.restoreSession().subscribe(value => expect(value).toBeTrue());
    http.expectNone(`${API_URL}/auth/me`);
  });

  for (const [label, value] of [
    ['expirado', token({ authSource: 'lactec', exp: 1 })], ['malformado', 'invalid.token'],
    ['sem validade', token({ role: 'Client' })], ['com papel inválido', token({ role: 123, exp: Date.now() / 1000 + 900 })],
  ]) it(`limpa sessão com JWT ${label}`, () => {
    localStorage.setItem(LS_TOKEN, value);
    localStorage.setItem(LS_USERNAME, 'Nome antigo');
    service.restoreSession().subscribe(result => expect(result).toBeFalse());
    expect(localStorage.getItem(LS_TOKEN)).toBeNull();
    expect(localStorage.getItem(LS_USERNAME)).toBeNull();
    expect(service.currentIdentity()).toBeNull();
    http.expectNone(`${API_URL}/auth/me`);
  });

  it('recusa /me 401 e remove o perfil persistido', () => {
    localStorage.setItem(LS_TOKEN, token({ authSource: 'lactec', exp: Date.now() / 1000 + 900 }));
    service.restoreSession().subscribe(result => expect(result).toBeFalse());
    http.expectOne(`${API_URL}/auth/me`).flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(localStorage.getItem(LS_TOKEN)).toBeNull();
  });

  it('uma resposta antiga não restaura o usuário depois do logout', () => {
    localStorage.setItem(LS_TOKEN, token({ authSource: 'lactec', exp: Date.now() / 1000 + 900 }));
    service.restoreSession().subscribe(result => expect(result).toBeFalse());
    const request = http.expectOne(`${API_URL}/auth/me`);
    service.logout();
    request.flush(identity);
    expect(service.currentUser()).toBeNull();
    expect(localStorage.getItem(LS_TOKEN)).toBeNull();
  });
});
