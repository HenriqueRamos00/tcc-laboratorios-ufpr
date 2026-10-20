import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';

import { CsrfStore } from '@core/store/csrf.store';

const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS']);

export const adminCsrfInterceptor: HttpInterceptorFn = (req, next) => {
  const csrf = inject(CsrfStore);
  const token = csrf.token();
  const isSetup = req.url.includes('/api/setup');

  if (!token || isSetup || SAFE_METHODS.has(req.method)) {
    return next(req);
  }

  return next(req.clone({ setHeaders: { 'X-CSRF-TOKEN': token } }));
};
