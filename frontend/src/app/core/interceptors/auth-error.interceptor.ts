import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { LoginService, tokenGetter } from '@/app/services/login.service';
import { isPortalProtectedRequest } from './portal-request';

export const authErrorInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);
  const loginService = inject(LoginService);
  const sessionToken = tokenGetter();
  const authorization = req.headers.get('Authorization');
  const usesSession = !authorization || authorization === `Bearer ${sessionToken}`;

  return next(req).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status === 401 && isPortalProtectedRequest(req.url) && usesSession && sessionToken === tokenGetter()) {
        loginService.logout();
        router.navigate(['/login']);
      }
      return throwError(() => err);
    }),
  );
};
