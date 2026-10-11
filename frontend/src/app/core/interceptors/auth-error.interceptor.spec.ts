import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';

import { API_URL } from '@/app/environment/env';
import { LoginService } from '@/app/services/login.service';
import { authErrorInterceptor } from './auth-error.interceptor';

describe('Interceptor de autenticação', () => {
  let http: HttpTestingController;
  let client: HttpClient;
  let login: jasmine.SpyObj<LoginService>;
  let router: jasmine.SpyObj<Router>;
  let snackBar: jasmine.SpyObj<MatSnackBar>;

  beforeEach(() => {
    login = jasmine.createSpyObj<LoginService>('LoginService', ['authorizationHeader', 'logout']);
    login.authorizationHeader.and.returnValue('Bearer token-de-teste');
    router = jasmine.createSpyObj<Router>('Router', ['navigateByUrl']);
    snackBar = jasmine.createSpyObj<MatSnackBar>('MatSnackBar', ['open']);

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authErrorInterceptor])),
        provideHttpClientTesting(),
        { provide: LoginService, useValue: login },
        { provide: Router, useValue: router },
        { provide: MatSnackBar, useValue: snackBar },
      ],
    });
    client = TestBed.inject(HttpClient);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('limpa a sessão e solicita novo login após resposta 401', () => {
    client.get(`${API_URL}/client/quotes`).subscribe({ error: () => undefined });
    const request = http.expectOne(`${API_URL}/client/quotes`);
    expect(request.request.headers.get('Authorization')).toBe('Bearer token-de-teste');
    request.flush({}, { status: 401, statusText: 'Não autorizado' });

    expect(login.logout).toHaveBeenCalledTimes(1);
    expect(router.navigateByUrl).toHaveBeenCalledOnceWith('/login');
    expect(snackBar.open).not.toHaveBeenCalled();
  });

  it('informa uma resposta 403 e mantém a sessão ativa', () => {
    client.get(`${API_URL}/client/quotes`).subscribe({ error: () => undefined });
    const request = http.expectOne(`${API_URL}/client/quotes`);
    expect(request.request.headers.get('Authorization')).toBe('Bearer token-de-teste');
    request.flush({}, { status: 403, statusText: 'Proibido' });

    expect(login.logout).not.toHaveBeenCalled();
    expect(router.navigateByUrl).not.toHaveBeenCalled();
    expect(snackBar.open).toHaveBeenCalled();
  });
});
