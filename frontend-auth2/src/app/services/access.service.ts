import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

import { environment } from '@/app/environment/env';
import {
  ApplicationToken,
  CreateRequests,
  IssuedToken,
  Records,
  Resource,
  UpdateRequests,
} from '@/app/model/access';

@Injectable({ providedIn: 'root' })
export class AccessService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  list<K extends Resource>(resource: K) {
    return this.http.get<Records[K][]>(`${this.base}/${resource}`);
  }

  get<K extends Resource>(resource: K, id: string) {
    return this.http.get<Records[K]>(`${this.base}/${resource}/${encodeURIComponent(id)}`);
  }

  create<K extends Resource>(resource: K, body: CreateRequests[K]) {
    return this.http.post<Records[K]>(`${this.base}/${resource}`, body);
  }

  update<K extends Resource>(resource: K, id: string, body: UpdateRequests[K]) {
    return this.http.put<Records[K]>(`${this.base}/${resource}/${encodeURIComponent(id)}`, body);
  }

  remove(resource: Resource, id: string) {
    return this.http.delete<void>(`${this.base}/${resource}/${encodeURIComponent(id)}`);
  }

  private tokenUrl(serviceId: string) {
    return `${this.base}/services/${encodeURIComponent(serviceId)}/tokens`;
  }

  tokens(serviceId: string) {
    return this.http.get<ApplicationToken[]>(this.tokenUrl(serviceId));
  }

  issue(serviceId: string, name: string, expiresIn: number | null) {
    return this.http.post<IssuedToken>(this.tokenUrl(serviceId), { name, expiresIn });
  }

  revoke(serviceId: string, tokenId: string) {
    return this.http.delete<void>(`${this.tokenUrl(serviceId)}/${encodeURIComponent(tokenId)}`);
  }
}

export function accessError(error: unknown, fallback: string): string {
  if (error instanceof HttpErrorResponse) {
    switch (error.status) {
      case 0:
        return 'Sem conexão com o serviço. Confira a conexão e tente novamente.';
      case 403:
        return 'Sua conta não tem permissão para esta operação.';
      case 404:
        return 'O registro solicitado já não está disponível. Atualize os dados.';
      case 503:
        return 'Serviço temporariamente indisponível. Tente novamente em instantes.';
    }
  }
  return fallback;
}
