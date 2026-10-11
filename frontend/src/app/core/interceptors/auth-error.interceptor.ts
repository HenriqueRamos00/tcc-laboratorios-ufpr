import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { API_URL } from '@/app/environment/env';
import { LoginService } from '@/app/services/login.service';

function isConfiguredApiRequest(url: string): boolean {
  const origin = typeof window === 'undefined' ? 'http://localhost' : window.location.origin;
  const configuredApi = new URL(API_URL, origin);
  const requestUrl = new URL(url, origin);
  const apiPath = configuredApi.pathname.replace(/\/$/, '');
  return requestUrl.origin === configuredApi.origin && requestUrl.pathname.startsWith(`${apiPath}/`);
}

function isLoginRequest(url: string): boolean {
  return new URL(url, typeof window === 'undefined' ? 'http://localhost' : window.location.origin)
    .pathname.endsWith('/auth/login');
}

export const authErrorInterceptor: HttpInterceptorFn = (request, next) => {
  const router = inject(Router);
  const loginService = inject(LoginService);
  const snackBar = inject(MatSnackBar);
  const isApiRequest = isConfiguredApiRequest(request.url);
  const isLogin = isLoginRequest(request.url);
  const authorization = isApiRequest && !isLogin ? loginService.authorizationHeader() : null;
  const authenticatedRequest = authorization
    ? request.clone({ setHeaders: { Authorization: authorization } })
    : request;

  return next(authenticatedRequest).pipe(
    catchError((error: HttpErrorResponse) => {
      if (isApiRequest && error.status === 401 && !isLogin) {
        loginService.logout();
        void router.navigateByUrl('/login');
      } else if (isApiRequest && error.status === 403) {
        snackBar.open('Você não tem permissão para acessar este recurso.', 'Fechar', {
          duration: 6000,
        });
      }
      return throwError(() => error);
    }),
  );
};
