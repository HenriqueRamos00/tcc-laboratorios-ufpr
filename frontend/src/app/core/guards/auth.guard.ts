import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { JwtHelperService } from '@auth0/angular-jwt';

import { LoginService, LS_TOKEN } from '@/app/services/login.service';

import { Role, UserRole } from '../store/user-role.store';

export const authGuard: CanActivateFn = (route) => {
  const router = inject(Router);
  const jwt = inject(JwtHelperService);
  const loginService = inject(LoginService);
  const userRole = inject(UserRole);

  const requiredRole = route.data['role'] as Role | undefined;
  const token = typeof localStorage !== 'undefined' ? localStorage.getItem(LS_TOKEN) : null;

  if (!token || jwt.isTokenExpired(token)) {
    loginService.logout();
    return router.parseUrl('/login');
  }

  const current = userRole.role();
  if (!current) return router.parseUrl('/login');

  if (requiredRole && current.id !== requiredRole.id) {
    return router.parseUrl(userRole.dashboardPath());
  }

  return true;
};
