import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, Observable, switchMap, throwError } from 'rxjs';

import { LoginService, tokenGetter } from '@/app/services/login.service';
import { isPortalProtectedRequest } from './portal-request';

export const authErrorInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);
  const loginService = inject(LoginService);
  const sessionToken = tokenGetter();
  const authorization = req.headers.get('Authorization');
  const usesSession = !authorization || authorization === `Bearer ${sessionToken}`;

  const endSession = (err: HttpErrorResponse): Observable<never> => {
    loginService.logout();
    router.navigate(['/login']);
    return throwError(() => err);
  };

  return next(req).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status !== 401 || !isPortalProtectedRequest(req.url) || !usesSession || sessionToken !== tokenGetter())
        return throwError(() => err);

      if (!loginService.canRenew()) return endSession(err);

      return loginService.renew().pipe(switchMap(() => {
        const current = tokenGetter();
        if (!current) return endSession(err);
        if (current === sessionToken) return throwError(() => err);

        return next(req.clone({ setHeaders: { Authorization: `Bearer ${current}` } })).pipe(
          catchError((retryError: HttpErrorResponse) =>
            retryError.status === 401 && tokenGetter() === current ? endSession(retryError) : throwError(() => retryError)));
      }));
    }),
  );
};
