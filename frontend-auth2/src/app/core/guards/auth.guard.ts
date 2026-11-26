import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { LoginService } from '@/app/services/login.service';

export const authGuard: CanActivateFn = () =>
  Boolean(inject(LoginService).accessToken()) || inject(Router).createUrlTree(['/login']);

export const guestGuard: CanActivateFn = () =>
  !inject(LoginService).accessToken() || inject(Router).createUrlTree(['/admin']);
