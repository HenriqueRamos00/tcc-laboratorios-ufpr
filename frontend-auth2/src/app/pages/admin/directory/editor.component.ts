import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  catchError,
  EMPTY,
  filter,
  finalize,
  forkJoin,
  map,
  merge,
  Observable,
  of,
  Subject,
  switchMap,
  takeUntil,
} from 'rxjs';

import { Application, Company, Records, Resource } from '@/app/model/access';
import { accessError, AccessService } from '@/app/services/access.service';
import { LoginService } from '@/app/services/login.service';
import { PageHeaderComponent } from '@shared/components/page-header/page-header.component';
import { ConfirmDialogComponent } from './confirm-dialog.component';
import { accessBreadcrumb, DirectorySection, formatCnpj } from './sections';
import { TokensComponent } from './tokens.component';

function blankForm() {
  return {
    name: '',
    code: '',
    email: '',
    password: '',
    active: true,
    cnpj: '',
    serviceId: '',
    companyIds: [] as string[],
  };
}

@Component({
  imports: [FormsModule, MatButtonModule, PageHeaderComponent, RouterLink, TokensComponent],
  templateUrl: './editor.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EditorComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly api = inject(AccessService);
  private readonly login = inject(LoginService);
  private readonly dialog = inject(MatDialog);
  private readonly snack = inject(MatSnackBar);
  private readonly destroy = inject(DestroyRef);
  private readonly retry = new Subject<void>();
  readonly section: DirectorySection = this.route.snapshot.data['section'];
  readonly resource = this.section.resource;
  readonly basePath = '/admin/' + this.section.path;
  readonly id = signal<string | null>(null);
  readonly record = signal<Records[Resource] | null>(null);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly loadError = signal('');
  readonly saveError = signal('');
  readonly tokensTab = toSignal(
    this.route.queryParamMap.pipe(map((params) => params.get('aba') === 'tokens')),
  );
  readonly formatCnpj = formatCnpj;
  apps: Application[] = [];
  companies: Company[] = [];
  hasActiveApplication = false;
  form = blankForm();

  constructor() {
    merge(this.route.paramMap, this.retry)
      .pipe(
        switchMap(() => {
          const id = this.route.snapshot.paramMap.get('id');
          this.id.set(id);
          this.loading.set(true);
          this.loadError.set('');
          this.saveError.set('');
          this.record.set(null);
          this.form = blankForm();
          return forkJoin({
            record: id ? this.api.get(this.resource, id) : of(null),
            apps: this.resource === 'users' ? this.api.list('services') : of([]),
            companies: this.resource === 'users' ? this.api.list('companies') : of([]),
          }).pipe(
            catchError((e) => {
              this.loadError.set(accessError(e, 'Não foi possível carregar o formulário.'));
              this.loading.set(false);
              return EMPTY;
            }),
          );
        }),
        takeUntilDestroyed(this.destroy),
      )
      .subscribe(({ record, apps, companies }) => {
        this.apps = apps;
        this.hasActiveApplication = apps.some((app) => app.active);
        this.companies = companies;
        this.record.set(record);
        if (record) {
          this.form.name = record.name;
          if ('cnpj' in record) this.form.cnpj = formatCnpj(record.cnpj);
          if ('code' in record) this.form.code = record.code;
          if ('active' in record) this.form.active = record.active;
          if ('email' in record) this.form.email = record.email;
          if ('companyIds' in record) {
            this.form.companyIds = [...record.companyIds];
            this.form.serviceId = record.serviceId;
          }
        }
        this.loading.set(false);
      });
  }

  get title() {
    return this.id() ? this.section.edit : this.section.create;
  }

  get breadcrumbs() {
    return [
      accessBreadcrumb,
      { label: this.section.label, route: this.basePath },
      { label: this.record()?.name || this.title },
    ];
  }

  get confirmedEmail() {
    const record = this.record();
    return record && 'emailConfirmed' in record && record.emailConfirmed;
  }

  reload() {
    this.retry.next();
  }

  selectCompany(id: string, selected: boolean) {
    this.form.companyIds = selected
      ? [...this.form.companyIds, id]
      : this.form.companyIds.filter((current) => current !== id);
  }

  save() {
    if (this.loading() || this.saving() || this.loadError()) return;
    this.saveError.set('');
    const f = this.form;
    if (!f.name.trim()) {
      this.saveError.set('Informe um nome.');
      return;
    }
    if (
      f.password &&
      (Array.from(f.password).length < 8 ||
        !/\p{Uppercase}/u.test(f.password) ||
        !/\p{Lowercase}/u.test(f.password) ||
        !/\p{Nd}/u.test(f.password))
    ) {
      this.saveError.set('Use pelo menos 8 caracteres, com maiúscula, minúscula e número.');
      return;
    }
    const id = this.id();
    const previous = this.record();
    const deactivating = previous && 'active' in previous && previous.active && !f.active;
    let confirmed: Observable<boolean> = of(true);
    if (deactivating && (this.resource === 'services' || this.resource === 'admins')) {
      confirmed = this.dialog
        .open(ConfirmDialogComponent, {
          data: {
            title: `Desativar ${previous.name}?`,
            message:
              this.resource === 'services'
                ? 'O acesso da aplicação será interrompido imediatamente.'
                : 'As sessões deste administrador serão encerradas.',
            action: 'Desativar',
          },
        })
        .afterClosed();
    }
    this.saving.set(true);
    confirmed
      .pipe(
        filter(Boolean),
        switchMap(() => this.write(id)),
        takeUntil(this.route.paramMap.pipe(filter((params) => params.get('id') !== id))),
        finalize(() => this.saving.set(false)),
        takeUntilDestroyed(this.destroy),
      )
      .subscribe({
        next: (saved) => {
          const ownAccount = this.resource === 'admins' && id === this.login.currentUser()?.id;
          if (ownAccount && (f.password || !f.active)) {
            this.snack.open('Conta atualizada. Sua sessão foi encerrada.', 'Fechar', {
              duration: 7000,
            });
            this.login.signOut();
            return;
          }
          if (ownAccount && 'email' in saved) this.login.updateCurrentUser(saved);
          const notice =
            this.resource === 'users' && !id
              ? 'Cliente cadastrado. O acesso depende da confirmação por e-mail.'
              : 'Alterações salvas.';
          this.snack.open(notice, 'Fechar', { duration: 5000 });
          void this.router.navigate([this.basePath]);
        },
        error: (e) => this.saveError.set(this.writeError(e)),
      });
  }

  private write(id: string | null): Observable<Records[Resource]> {
    const { name, email, password, code, active, serviceId, companyIds } = this.form;
    switch (this.resource) {
      case 'services':
        return id
          ? this.api.update('services', id, { name, code, active })
          : this.api.create('services', { name, code });
      case 'companies': {
        const data = { name, cnpj: this.form.cnpj.replace(/[./-]/g, '') };
        return id ? this.api.update('companies', id, data) : this.api.create('companies', data);
      }
      case 'admins':
        return id
          ? this.api.update('admins', id, {
              name,
              email,
              active,
              ...(password ? { password } : {}),
            })
          : this.api.create('admins', { name, email, password });
      case 'users':
        return id
          ? this.api.update('users', id, { name, active, companyIds })
          : this.api.create('users', { name, email, password, serviceId, companyIds });
    }
  }

  private writeError(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.status === 409) {
      switch (this.resource) {
        case 'services':
          return 'Já existe uma aplicação com este código.';
        case 'companies':
          return 'Já existe uma empresa com este CNPJ.';
        case 'users':
          return error.error?.detail === 'Servico inativo'
            ? 'A aplicação selecionada foi desativada. Escolha outra aplicação.'
            : 'Este e-mail já está cadastrado na aplicação selecionada.';
        case 'admins':
          return this.id() && !this.form.active
            ? 'Verifique se há outro administrador ativo e se o e-mail já está em uso.'
            : 'Já existe um administrador com este e-mail.';
      }
    }
    if (error instanceof HttpErrorResponse && error.status === 400)
      return 'Confira os campos informados e tente novamente.';
    return accessError(error, 'Não foi possível salvar as alterações.');
  }
}
