import { computed } from '@angular/core';
import { patchState, signalStore, withComputed, withMethods, withState } from '@ngrx/signals';

import { InternalRole, InternalUser } from '@/app/model/internal-user';

export const USER_STORAGE_KEY = 'lactec.internal.user';

export interface UserRoleState {
  user: InternalUser | null;
}

function isInternalUser(value: unknown): value is InternalUser {
  if (!value || typeof value !== 'object') return false;
  const user = value as Partial<InternalUser>;
  return (
    typeof user.id === 'number' &&
    typeof user.name === 'string' &&
    typeof user.email === 'string' &&
    (user.role === 'ADMIN' || user.role === 'TECNICO')
  );
}

function loadUser(): InternalUser | null {
  if (typeof localStorage === 'undefined' || typeof sessionStorage === 'undefined') return null;
  for (const storage of [localStorage, sessionStorage]) {
    const raw = storage.getItem(USER_STORAGE_KEY);
    if (!raw) continue;
    try {
      const parsed: unknown = JSON.parse(raw);
      if (isInternalUser(parsed)) return parsed;
    } catch {
      storage.removeItem(USER_STORAGE_KEY);
    }
  }
  return null;
}

const initialState: UserRoleState = { user: loadUser() };

export const UserRole = signalStore(
  { providedIn: 'root' },
  withState(initialState),
  withComputed(({ user }) => ({
    role: computed(() => user()?.role ?? null),
    dashboardPath: computed(() => {
      const role = user()?.role;
      if (role === 'ADMIN') return '/admin';
      if (role === 'TECNICO') return '/tecnico';
      return '/login';
    }),
  })),
  withMethods((store) => ({
    setUser(user: InternalUser, keepConnected: boolean): void {
      const selectedStorage = keepConnected ? localStorage : sessionStorage;
      const otherStorage = keepConnected ? sessionStorage : localStorage;
      selectedStorage.setItem(USER_STORAGE_KEY, JSON.stringify(user));
      otherStorage.removeItem(USER_STORAGE_KEY);
      patchState(store, { user });
    },
    clear(): void {
      localStorage.removeItem(USER_STORAGE_KEY);
      sessionStorage.removeItem(USER_STORAGE_KEY);
      patchState(store, { user: null });
    },
  })),
);

export function isInternalRole(value: unknown): value is InternalRole {
  return value === 'ADMIN' || value === 'TECNICO';
}
