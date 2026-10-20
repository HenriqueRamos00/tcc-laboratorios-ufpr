import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { AdminSessionStore } from '@core/store/admin-session.store';

export const adminErrorInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);
  const store = inject(AdminSessionStore);
  const snackBar = inject(MatSnackBar);
  const authRequest =
    req.url.includes('/api/admin/auth/') || req.url.includes('/api/setup');

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      if (!authRequest && error.status === 401) {
        store.clear();
        void router.navigateByUrl('/login');
      } else if (!authRequest && error.status === 403) {
        snackBar.open('Sua conta não tem permissão para acessar este recurso.', 'Fechar', {
          duration: 5000,
        });
      }
      return throwError(() => error);
    }),
  );
};
