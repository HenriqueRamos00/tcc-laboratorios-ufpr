import { computed } from '@angular/core';
import { patchState, signalStore, withComputed, withMethods, withState } from '@ngrx/signals';

export interface Role {
  id: number;
  name: 'admin' | 'client';
}

export interface RoleState {
  role: Role | null;
}

export const UserRole = signalStore(
  { providedIn: 'root' },
  withState<RoleState>({ role: null }),
  withComputed(({ role }) => ({
    isClient: computed(() => role()?.name === 'client'),
    dashboardPath: computed(() => (role()?.name === 'client' ? '/cliente' : '/login')),
  })),
  withMethods((store) => ({
    setRole(role: Role): void {
      patchState(store, { role });
    },
    clear(): void {
      patchState(store, { role: null });
    },
  })),
);
