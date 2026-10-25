import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';

import { LoginService } from '@/app/services/login.service';
import { Role, UserRole } from '../store/user-role.store';

export const authGuard: CanActivateFn = route => {
  const router = inject(Router);
  const loginService = inject(LoginService);
  const userRole = inject(UserRole);
  const requiredRole = route.data['role'] as Role | undefined;
  return loginService.restoreSession().pipe(map(authenticated => {
    if (!authenticated) return router.parseUrl('/login');
    const current = userRole.role();
    if (!current) return router.parseUrl('/login');
    return requiredRole && current.id !== requiredRole.id
      ? router.parseUrl(userRole.dashboardPath()) : true;
  }));
};
