import { computed, Injectable, signal } from '@angular/core';

import { AdminSession } from '@/app/model/admin-user';

@Injectable({ providedIn: 'root' })
export class AdminSessionStore {
  private readonly current = signal<AdminSession | null>(null);

  readonly user = this.current.asReadonly();
  readonly isAuthenticated = computed(() => this.current() !== null);
  readonly displayName = computed(() => this.current()?.name ?? '');

  setUser(user: AdminSession): void {
    this.current.set(user);
  }

  clear(): void {
    this.current.set(null);
  }
}
