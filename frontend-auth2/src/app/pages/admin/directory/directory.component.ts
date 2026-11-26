import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { filter, finalize, forkJoin, map, Observable, switchMap, tap } from 'rxjs';

import { Records, Resource } from '@/app/model/access';
import { AccessService, accessError } from '@/app/services/access.service';
import { LoginService } from '@/app/services/login.service';
import { PageHeaderComponent } from '@shared/components/page-header/page-header.component';
import { ConfirmDialogComponent } from './confirm-dialog.component';
import { accessBreadcrumb, DirectorySection, formatCnpj } from './sections';

interface Cell {
  text: string;
  tone?: 'positive' | 'neutral' | 'pending';
}

interface Row {
  record: Records[Resource];
  subtitle?: string;
  cells: Cell[];
}

function status(active: boolean, feminine = false): Cell {
  if (feminine)
    return { text: active ? 'Ativa' : 'Inativa', tone: active ? 'positive' : 'neutral' };
  return { text: active ? 'Ativo' : 'Inativo', tone: active ? 'positive' : 'neutral' };
}

function searchable(value: string) {
  return value
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLocaleLowerCase('pt-BR');
}

@Component({
  imports: [MatButtonModule, MatIconModule, PageHeaderComponent, RouterLink],
  templateUrl: './directory.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DirectoryComponent {
  private readonly api = inject(AccessService);
  private readonly login = inject(LoginService);
  private readonly dialog = inject(MatDialog);
  private readonly destroy = inject(DestroyRef);
  readonly section: DirectorySection = inject(ActivatedRoute).snapshot.data['section'];
  readonly basePath = '/admin/' + this.section.path;
  readonly breadcrumbs = [accessBreadcrumb, { label: this.section.label }];
  readonly loading = signal(true);
  readonly loadError = signal('');
  readonly message = signal('');
  readonly actionError = signal('');
  readonly busy = signal(false);
  readonly rows = signal<Row[]>([]);
  readonly query = signal('');
  readonly shown = computed(() => {
    const word = searchable(this.query().trim());
    return this.rows().filter(({ record, subtitle, cells }) => {
      const terms = [record.name, subtitle, ...cells.map((cell) => cell.text)];
      if ('cnpj' in record) terms.push(record.cnpj);
      return searchable(terms.join(' ')).includes(word);
    });
  });

  constructor() {
    this.reload();
  }

  reload() {
    this.loadError.set('');
    this.loading.set(true);
    this.fetchRows()
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (rows) => {
          this.rows.set(rows);
          this.loading.set(false);
        },
        error: (e) => {
          this.loadError.set(accessError(e, 'Falha ao carregar os registros.'));
          this.loading.set(false);
        },
      });
  }

  private fetchRows(): Observable<Row[]> {
    switch (this.section.resource) {
      case 'services':
        return this.api.list('services').pipe(
          map((items) =>
            items.map((record) => ({
              record,
              cells: [{ text: record.code }, status(record.active, true)],
            })),
          ),
        );
      case 'companies':
        return this.api.list('companies').pipe(
          map((items) =>
            items.map((record) => ({
              record,
              cells: [{ text: formatCnpj(record.cnpj) }],
            })),
          ),
        );
      case 'admins':
        return this.api.list('admins').pipe(
          map((items) =>
            items.map((record) => ({
              record,
              cells: [{ text: record.email }, status(record.active)],
            })),
          ),
        );
      case 'users':
        return forkJoin({
          clients: this.api.list('users'),
          apps: this.api.list('services'),
          companies: this.api.list('companies'),
        }).pipe(
          map(({ clients, apps, companies }) => {
            const appNames = new Map(apps.map((app) => [app.id, app.name]));
            const companyNames = new Map(companies.map((company) => [company.id, company.name]));
            return clients.map((record) => ({
              record,
              subtitle: record.email,
              cells: [
                { text: appNames.get(record.serviceId) ?? record.serviceId },
                {
                  text:
                    record.companyIds.map((id) => companyNames.get(id) ?? id).join(', ') ||
                    'Sem vínculo',
                },
                status(record.active),
                {
                  text: record.emailConfirmed ? 'Confirmado' : 'Pendente',
                  tone: record.emailConfirmed ? 'positive' : 'pending',
                },
              ],
            }));
          }),
        );
    }
  }

  remove(record: Records[Resource]) {
    if (this.busy()) return;
    const resource = this.section.resource;
    const deactivate = resource === 'services';
    const action = deactivate ? 'Desativar' : 'Excluir';
    this.dialog
      .open(ConfirmDialogComponent, {
        data: {
          title: `${action} ${record.name}?`,
          message: deactivate
            ? 'Os tokens desta aplicação deixarão de permitir acesso imediatamente.'
            : 'A exclusão é permanente.',
          action,
        },
      })
      .afterClosed()
      .pipe(
        filter(Boolean),
        tap(() => {
          this.busy.set(true);
          this.actionError.set('');
          this.message.set('');
        }),
        switchMap(() => this.api.remove(resource, record.id)),
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroy),
      )
      .subscribe({
        next: () => {
          if (resource === 'admins' && record.id === this.login.currentUser()?.id) {
            this.login.signOut();
            return;
          }
          this.message.set(deactivate ? 'Aplicação desativada.' : 'Registro excluído.');
          this.reload();
        },
        error: (e) => {
          let detail = 'Não foi possível concluir a operação.';
          if (e instanceof HttpErrorResponse && e.status === 409) {
            if (resource === 'companies')
              detail =
                'Esta empresa está vinculada a clientes. Remova os vínculos nas contas antes de excluí-la.';
            if (resource === 'admins')
              detail = 'É necessário manter pelo menos um administrador ativo.';
          }
          this.actionError.set(accessError(e, detail));
        },
      });
  }

  canRemove(record: Records[Resource]) {
    return !('code' in record) || record.active;
  }
}
