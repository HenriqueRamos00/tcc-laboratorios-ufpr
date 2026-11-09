import type { HttpResponse } from '@angular/common/http';

export const DEFAULT_PAGE_SIZE = 50;

export interface PageRequest {
  readonly page: number;
  readonly pageSize: number;
}

export interface Page<T> {
  readonly items: readonly T[];
  readonly total: number;
  readonly page: number;
  readonly pageSize: number;
}

export function pageParams(request: PageRequest): Record<string, string> {
  return { page: String(request.page), pageSize: String(request.pageSize) };
}

function headerNumber(response: HttpResponse<unknown>, name: string): number | null {
  const value = Number(response.headers.get(name));
  return response.headers.has(name) && Number.isInteger(value) && value >= 0 ? value : null;
}

export function toPage<T>(response: HttpResponse<T[]>, request: PageRequest): Page<T> {
  const items = response.body ?? [];
  const page = headerNumber(response, 'X-Page') || request.page;
  const pageSize = headerNumber(response, 'X-Page-Size') || request.pageSize;
  const total = headerNumber(response, 'X-Total-Count') ?? (page - 1) * pageSize + items.length;
  return { items, total, page, pageSize };
}
