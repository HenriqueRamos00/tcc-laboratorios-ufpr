import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { catchError, throwError } from 'rxjs';

import { LoginService } from '@/app/services/login.service';

export const authErrorInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);
  const loginService = inject(LoginService);
  const snackBar = inject(MatSnackBar);

  return next(req).pipe(
    catchError((err: HttpErrorResponse) => {
      const isLoginRequest = req.url.endsWith('/auth/internal/login');
      if (!isLoginRequest && err.status === 401) {
        loginService.logout();
        void router.navigateByUrl('/login');
      } else if (!isLoginRequest && err.status === 403) {
        snackBar.open('Sua conta não tem permissão para acessar este recurso.', 'Fechar', { duration: 5000 });
      }
      return throwError(() => err);
    }),
  );
};
