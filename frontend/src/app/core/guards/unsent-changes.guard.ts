import { CanDeactivateFn } from '@angular/router';
import { Observable } from 'rxjs';

import { tokenGetter } from '@/app/services/login.service';

export interface ConfirmsLeaving {
  hasUnsentChanges(): boolean;
  confirmLeaving(): Observable<boolean>;
}

export const unsentChangesGuard: CanDeactivateFn<ConfirmsLeaving> = (component) => {
  if (!tokenGetter() || !component.hasUnsentChanges()) return true;
  return component.confirmLeaving();
};
