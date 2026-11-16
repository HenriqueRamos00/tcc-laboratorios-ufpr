import type { PageRequest } from '@/app/model/page';

export interface DataTableQuery extends PageRequest {
  readonly chosenPageSize?: number | null;
}

export type DataTableCellValue = string | number | null;

export interface DataTableColumn<T> {
  readonly key: string;
  readonly header: string;
  readonly value?: (row: T) => DataTableCellValue;
  readonly align?: 'start' | 'end';
}

export interface DataTableRowContext<T> {
  readonly $implicit: T;
}

export function unmeasuredQuery(): DataTableQuery {
  return { page: 1, pageSize: 0 };
}

export function isMeasured(query: DataTableQuery): boolean {
  return query.pageSize > 0;
}

export function sameQuery(left: DataTableQuery, right: DataTableQuery): boolean {
  return left.page === right.page && left.pageSize === right.pageSize;
}

export function pageKeepingFirstRow(query: DataTableQuery, pageSize: number): number {
  return Math.floor(((query.page - 1) * query.pageSize) / pageSize) + 1;
}
