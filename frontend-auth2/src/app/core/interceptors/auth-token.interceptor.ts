import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { tap, throwError } from 'rxjs';

import { environment } from '@/app/environment/env';
import { LoginService, loginUrl } from '@/app/services/login.service';

export const authTokenInterceptor: HttpInterceptorFn = (request, next) => {
  if (!request.url.startsWith(`${environment.apiUrl}/`) || request.url === loginUrl) {
    return next(request);
  }

  const login = inject(LoginService);
  const token = login.accessToken();
  if (!token) {
    login.signOut();
    return throwError(() => new HttpErrorResponse({ status: 401, url: request.url }));
  }

  return next(request.clone({ setHeaders: { Authorization: `Bearer ${token}` } })).pipe(
    tap({
      error: (error: unknown) => {
        if (
          error instanceof HttpErrorResponse &&
          error.status === 401 &&
          login.accessToken() === token
        ) {
          login.signOut();
        }
      },
    }),
  );
};
