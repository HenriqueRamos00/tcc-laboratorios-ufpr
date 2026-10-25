import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, provideRouter, RouterStateSnapshot, UrlTree } from '@angular/router';
import { firstValueFrom, Observable, of } from 'rxjs';
import { LoginService } from '@/app/services/login.service';
import { UserRole } from '../store/user-role.store';
import { authGuard } from './auth.guard';

describe('authGuard', () => {
  for (const [authenticated, role, expected] of [
    [false, null, '/login'], [true, { id: 2, name: 'client' }, true], [true, null, '/login'],
  ] as const) it(`restaura sessão antes de decidir acesso: ${expected}`, async () => {
    TestBed.configureTestingModule({ providers: [provideRouter([]),
      { provide: LoginService, useValue: { restoreSession: () => of(authenticated) } },
      { provide: UserRole, useValue: { role: () => role, dashboardPath: () => '/cliente' } },
    ] });
    const result = TestBed.runInInjectionContext(() => authGuard(
      { data: { role: { id: 2, name: 'client' } } } as unknown as ActivatedRouteSnapshot,
      {} as RouterStateSnapshot,
    )) as Observable<boolean | UrlTree>;
    const decision = await firstValueFrom(result);
    expect(decision instanceof UrlTree ? decision.toString() : decision).toBe(expected);
  });
});
