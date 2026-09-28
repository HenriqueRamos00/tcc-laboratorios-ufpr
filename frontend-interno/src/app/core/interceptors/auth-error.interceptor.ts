import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { LoginService } from '@/app/services/login.service';

export const authErrorInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);
  const loginService = inject(LoginService);

  return next(req).pipe(
    catchError((err: HttpErrorResponse) => {
      const isLoginRequest = req.url.endsWith('/auth/internal/login');
      if (!isLoginRequest && (err.status === 401 || err.status === 403)) {
        loginService.logout();
        void router.navigateByUrl('/login');
      }
      return throwError(() => err);
    }),
  );
};
