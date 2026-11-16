import { Directive, inject, input, TemplateRef } from '@angular/core';

import type { DataTableRowContext } from './data-table.model';

@Directive({
  selector: 'ng-template[appDataTableActions]',
  standalone: true,
})
export class DataTableActionsDirective<T> {
  readonly rows = input.required<readonly T[]>({ alias: 'appDataTableActionsOf' });

  readonly template = inject<TemplateRef<DataTableRowContext<T>>>(TemplateRef);

  static ngTemplateContextGuard<T>(
    directive: DataTableActionsDirective<T>,
    context: unknown,
  ): context is DataTableRowContext<T> {
    return true;
  }
}
