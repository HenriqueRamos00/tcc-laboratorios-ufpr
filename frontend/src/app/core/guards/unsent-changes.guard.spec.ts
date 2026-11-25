import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { Observable, of } from 'rxjs';

import { LS_TOKEN } from '@/app/services/login.service';

import { unsentChangesGuard, type ConfirmsLeaving } from './unsent-changes.guard';

describe('unsentChangesGuard', () => {
  function leaving(screen: ConfirmsLeaving): boolean | Observable<boolean> {
    return TestBed.runInInjectionContext(() =>
      unsentChangesGuard(
        screen,
        {} as ActivatedRouteSnapshot,
        {} as RouterStateSnapshot,
        {} as RouterStateSnapshot,
      ),
    ) as boolean | Observable<boolean>;
  }

  function screenWith(changes: boolean, answer: Observable<boolean>) {
    return {
      hasUnsentChanges: jasmine.createSpy('hasUnsentChanges').and.returnValue(changes),
      confirmLeaving: jasmine.createSpy('confirmLeaving').and.returnValue(answer),
    };
  }

  beforeEach(() => sessionStorage.setItem(LS_TOKEN, 'token-de-teste'));
  afterEach(() => sessionStorage.removeItem(LS_TOKEN));

  it('deixa sair sem perguntar quando nada mudou', () => {
    const screen = screenWith(false, of(false));

    expect(leaving(screen)).toBeTrue();
    expect(screen.confirmLeaving).not.toHaveBeenCalled();
  });

  it('entrega a decisão à tela quando há alteração não enviada', () => {
    const answer = of(false);
    const screen = screenWith(true, answer);

    expect(leaving(screen)).toBe(answer);
  });

  it('não segura na tela quem já perdeu a sessão', () => {
    sessionStorage.removeItem(LS_TOKEN);
    const screen = screenWith(true, of(false));

    expect(leaving(screen)).toBeTrue();
    expect(screen.confirmLeaving).not.toHaveBeenCalled();
  });
});
