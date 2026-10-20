import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';

import { AdminSessionStore } from '@core/store/admin-session.store';
import { AdminAuthService } from '@/app/services/admin-auth.service';

export const adminAuthGuard: CanActivateFn = () => {
  const router = inject(Router);
  const store = inject(AdminSessionStore);
  const auth = inject(AdminAuthService);

  if (store.user()) return true;
  return auth.session().pipe(
    map(() => true),
    catchError(() => of(router.createUrlTree(['/login']))),
  );
};

export const guestGuard: CanActivateFn = () => {
  const router = inject(Router);
  const store = inject(AdminSessionStore);
  const auth = inject(AdminAuthService);

  if (store.user()) return router.createUrlTree(['/admin/users']);
  return auth.session().pipe(
    map(() => router.createUrlTree(['/admin/users'])),
    catchError(() => of(true)),
  );
};

export const setupGuard: CanActivateFn = () => {
  const router = inject(Router);
  const store = inject(AdminSessionStore);
  const auth = inject(AdminAuthService);

  if (store.user()) return router.createUrlTree(['/admin/users']);
  return auth.setupStatus().pipe(
    map((status) => (status.required ? true : router.createUrlTree(['/login']))),
    catchError(() => of(router.createUrlTree(['/login']))),
  );
};
