import { HttpInterceptorFn } from '@angular/common/http';

import { tokenGetter } from '@/app/services/login.service';

export const authTokenInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith('/api/') || req.url.endsWith('/auth/internal/login')) {
    return next(req);
  }

  const token = tokenGetter();
  return next(
    token
      ? req.clone({ setHeaders: { Authorization: 'Bearer ' + token } })
      : req,
  );
};
