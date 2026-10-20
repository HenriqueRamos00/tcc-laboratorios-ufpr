import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_URL } from '@/app/environment/env';
import { Company, CompanyRequest } from '@/app/model/company';

@Injectable({ providedIn: 'root' })
export class CompaniesService {
  private readonly http = inject(HttpClient);
  private readonly endpoint = `${API_URL}/admin/companies`;

  list(search = ''): Observable<Company[]> {
    let params = new HttpParams();
    if (search.trim()) params = params.set('search', search.trim());
    return this.http.get<Company[]>(this.endpoint, { params });
  }

  create(request: CompanyRequest): Observable<Company> {
    return this.http.post<Company>(this.endpoint, request);
  }

  update(id: string, request: CompanyRequest): Observable<Company> {
    return this.http.put<Company>(`${this.endpoint}/${encodeURIComponent(id)}`, request);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.endpoint}/${encodeURIComponent(id)}`);
  }
}
