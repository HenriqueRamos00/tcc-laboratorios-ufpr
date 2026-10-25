import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_URL } from '@/app/environment/env';
import { DashboardAdministrador } from '@/app/model/dashboard-administrador';

@Injectable({ providedIn: 'root' })
export class DashboardAdministradorService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = `${API_URL}/admin/dashboard`;

  get(): Observable<DashboardAdministrador> {
    return this.http.get<DashboardAdministrador>(this.endpoint);
  }
}
