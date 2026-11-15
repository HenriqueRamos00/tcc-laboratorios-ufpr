import { Directive, inject, input, TemplateRef } from '@angular/core';

import type { DataTableRowContext } from './data-table.model';

@Directive({
  selector: 'ng-template[appDataTableCell]',
  standalone: true,
})
export class DataTableCellDirective<T> {
  readonly key = input.required<string>({ alias: 'appDataTableCell' });
  readonly rows = input.required<readonly T[]>({ alias: 'appDataTableCellOf' });

  readonly template = inject<TemplateRef<DataTableRowContext<T>>>(TemplateRef);

  static ngTemplateContextGuard<T>(
    directive: DataTableCellDirective<T>,
    context: unknown,
  ): context is DataTableRowContext<T> {
    return true;
  }
}
