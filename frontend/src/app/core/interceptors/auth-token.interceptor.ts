import { HttpInterceptorFn } from '@angular/common/http';
import { tokenGetter } from '@/app/services/login.service';
import { isPortalProtectedRequest } from './portal-request';

export const authTokenInterceptor: HttpInterceptorFn = (request, next) => {
  const token = isPortalProtectedRequest(request.url) ? tokenGetter() : null;
  return next(token && !request.headers.has('Authorization')
    ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : request);
};
