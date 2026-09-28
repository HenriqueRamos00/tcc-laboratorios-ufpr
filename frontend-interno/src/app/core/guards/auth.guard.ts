import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { LoginService } from '@/app/services/login.service';
import { InternalRole } from '@/app/model/internal-user';
import { UserRole } from '../store/user-role.store';

export const authGuard: CanActivateFn = (route) => {
  const router = inject(Router);
  const loginService = inject(LoginService);
  const userRole = inject(UserRole);

  if (!loginService.hasValidSession()) {
    loginService.logout();
    return router.parseUrl('/login');
  }

  const requiredRole = route.data['role'] as InternalRole | undefined;
  const currentRole = userRole.role();
  if (!currentRole) {
    loginService.logout();
    return router.parseUrl('/login');
  }

  if (requiredRole && currentRole !== requiredRole) {
    return router.parseUrl(userRole.dashboardPath());
  }

  return true;
};

export const guestGuard: CanActivateFn = () => {
  const router = inject(Router);
  const loginService = inject(LoginService);
  const userRole = inject(UserRole);

  if (loginService.hasValidSession()) return router.parseUrl(userRole.dashboardPath());
  loginService.logout();
  return true;
};
