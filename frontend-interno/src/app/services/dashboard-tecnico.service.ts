import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_URL } from '@/app/environment/env';
import { DashboardTecnico } from '@/app/model/dashboard-tecnico';

@Injectable({ providedIn: 'root' })
export class DashboardTecnicoService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = `${API_URL}/technician/dashboard`;

  get(): Observable<DashboardTecnico> {
    return this.http.get<DashboardTecnico>(this.endpoint);
  }
}
