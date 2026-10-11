import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { LoginService } from '@/app/services/login.service';
import { Role, UserRole } from '../store/user-role.store';

export const authGuard: CanActivateFn = (route) => {
  const router = inject(Router);
  const loginService = inject(LoginService);
  const userRole = inject(UserRole);
  const requiredRole = route.data['role'] as Role | undefined;

  if (!loginService.hasValidSession()) return router.parseUrl('/login');

  const currentProfile = loginService.currentProfile();
  const currentRole = userRole.role();
  if (!currentProfile || currentProfile.role !== 'Client' || !currentRole) {
    loginService.logout();
    return router.parseUrl('/login');
  }

  if (requiredRole && currentRole.id !== requiredRole.id) {
    return router.parseUrl(userRole.dashboardPath());
  }

  return true;
};
