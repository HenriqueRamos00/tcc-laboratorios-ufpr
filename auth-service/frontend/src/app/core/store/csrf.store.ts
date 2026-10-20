import { Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class CsrfStore {
  private readonly current = signal<string | null>(null);

  readonly token = this.current.asReadonly();

  set(token: string): void {
    this.current.set(token);
  }

  clear(): void {
    this.current.set(null);
  }
}
