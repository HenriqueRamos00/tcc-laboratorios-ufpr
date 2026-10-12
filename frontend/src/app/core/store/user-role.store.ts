import { computed } from '@angular/core';
import { patchState, signalStore, withComputed, withMethods, withState } from '@ngrx/signals';

export interface Role {
  id: number;
  name: 'admin' | 'client';
}

export const ROLE_STORAGE_KEY = 'lactec.role';

export interface RoleState {
  role: Role | null;
}

function isRole(value: unknown): value is Role {
  return (
    !!value &&
    typeof (value as Role).id === 'number' &&
    ((value as Role).name === 'admin' || (value as Role).name === 'client')
  );
}

function loadRole(): Role | null {
  if (typeof localStorage === 'undefined') return null;
  const raw = localStorage.getItem(ROLE_STORAGE_KEY);
  if (!raw) return null;
  try {
    const parsed: unknown = JSON.parse(raw);
    return isRole(parsed) ? parsed : null;
  } catch {
    return null;
  }
}

function saveRole(role: Role): void {
  localStorage.setItem(ROLE_STORAGE_KEY, JSON.stringify(role));
}

function removeRole(): void {
  localStorage.removeItem(ROLE_STORAGE_KEY);
}

const initialState: RoleState = { role: loadRole() };

export const UserRole = signalStore(
  { providedIn: 'root' },
  withState(initialState),
  withComputed(({ role }) => ({
    isClient: computed(() => role()?.name === 'client'),
    dashboardPath: computed(() => {
      const papel = role();
      if (!papel) return '/login';
      return papel.name === 'client' ? '/cliente' : '/login';
    }),
  })),
  withMethods((store) => ({
    setRole(role: Role): void {
      patchState(store, { role });
      saveRole(role);
    },
    setRoleFromTokenClaim(claim: string): void {
      const role = mapClaimToRole(claim);
      if (role) {
        patchState(store, { role });
        saveRole(role);
      }
    },
    clear(): void {
      patchState(store, { role: null });
      removeRole();
    },
  })),
);

export function mapClaimToRole(claim: string): Role | null {
  switch (claim.toUpperCase()) {
    case 'CLIENTE':
    case 'CLIENT':
      return { id: 2, name: 'client' };
    default:
      return null;
  }
}
