import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { switchMap } from 'rxjs';

import { LoginService, tokenGetter } from '@/app/services/login.service';
import { isPortalProtectedRequest } from './portal-request';

export const authTokenInterceptor: HttpInterceptorFn = (request, next) => {
  if (!isPortalProtectedRequest(request.url) || request.headers.has('Authorization')) return next(request);

  const loginService = inject(LoginService);
  const send = () => {
    const token = tokenGetter();
    return next(token ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : request);
  };

  return loginService.renewalDue() ? loginService.renew().pipe(switchMap(send)) : send();
};
