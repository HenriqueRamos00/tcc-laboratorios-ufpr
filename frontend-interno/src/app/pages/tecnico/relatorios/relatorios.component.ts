import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { EMPTY, ReplaySubject, Subject } from 'rxjs';
import { catchError, debounceTime, distinctUntilChanged, finalize, startWith, switchMap, tap } from 'rxjs/operators';

import { Page } from '@/app/model/ensaio';
import {
  publicationStatusClass,
  publicationStatusLabel,
  validationStatusClass,
  validationStatusLabel,
} from '@/app/model/relatorio-presentation';
import {
  ReportDetail,
  ReportSummary,
  ReportValidationStatus,
} from '@/app/model/relatorio';
import { RelatoriosService } from '@/app/services/relatorios.service';
import { EmptyStateComponent } from '@/app/shared/components/empty-state/empty-state.component';
import { PageHeaderComponent } from '@/app/shared/components/page-header/page-header.component';
import { SearchFieldComponent } from '@/app/shared/components/search-field/search-field.component';
import { ReportDetailComponent } from './report-detail.component';

const REPORT_PAGE_SIZE = 20;
type ValidationFilter = 'all' | ReportValidationStatus;
type PublicationFilter = 'all' | 'published' | 'unpublished';

@Component({
  selector: 'app-relatorios',
  standalone: true,
  imports: [
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    EmptyStateComponent,
    PageHeaderComponent,
    SearchFieldComponent,
    ReportDetailComponent,
  ],
  templateUrl: './relatorios.component.html',
  styleUrl: './relatorios.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RelatoriosComponent {
  private readonly service = inject(RelatoriosService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly routeReportId = signal(this.route.snapshot.paramMap.get('id'));
  private readonly listRequests = new Subject<void>();
  private readonly detailRequests = new ReplaySubject<string>(1);

  readonly pageTitle = 'Relatórios';
  readonly breadcrumbLabel = 'Pesquisar relatório';

  readonly search = new FormControl('', { nonNullable: true });
  readonly reports = signal<ReportSummary[]>([]);
  readonly selectedId = signal<string | null>(this.routeReportId());
  readonly detail = signal<ReportDetail | null>(null);
  readonly page = signal(1);
  readonly pageSize = REPORT_PAGE_SIZE;
  readonly totalItems = signal(0);
  readonly validationFilter = signal<ValidationFilter>('all');
  readonly publicationFilter = signal<PublicationFilter>('all');
  readonly listLoading = signal(true);
  readonly listError = signal<string | null>(null);
  readonly detailLoading = signal(false);
  readonly detailError = signal<string | null>(null);
  readonly saving = signal(false);
  readonly validationConflict = signal(false);
  readonly validationMessage = signal<string | null>(null);
  readonly validationError = signal<string | null>(null);
  readonly publishing = signal(false);
  readonly publicationConflict = signal(false);
  readonly publicationMessage = signal<string | null>(null);
  readonly publicationError = signal<string | null>(null);
  readonly pdfLoading = signal(false);
  readonly pdfError = signal<string | null>(null);

  readonly pageCount = computed(() =>
    Math.max(1, Math.ceil(this.totalItems() / this.pageSize)),
  );
  readonly hasMoreThanOnePage = computed(() => this.totalItems() > this.pageSize);

  constructor() {
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const routeId = params.get('id');
      this.routeReportId.set(routeId);
      if (routeId) {
        const selectionChanged = this.selectedId() !== routeId;
        if (selectionChanged) {
          this.selectedId.set(routeId);
          this.detail.set(null);
          this.publicationMessage.set(null);
        }
        if (selectionChanged || (this.detail()?.id !== routeId && !this.detailLoading())) {
          this.detailRequests.next(routeId);
        }
      } else if (!routeId && this.selectedId()) {
        const reports = this.reports();
        const selected = reports.find((report) => report.id === this.selectedId()) ?? reports[0];
        this.selectedId.set(selected?.id ?? null);
        this.detail.set(null);
        this.publicationMessage.set(null);
        this.detailRequests.next(selected?.id ?? '');
      }
    });

    this.search.valueChanges
      .pipe(
        debounceTime(250),
        distinctUntilChanged(),
        tap(() => this.page.set(1)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(() => this.listRequests.next());

    this.listRequests
      .pipe(
        startWith(undefined),
        switchMap(() => {
          this.listLoading.set(true);
          this.listError.set(null);
          const validationFilter = this.validationFilter();
          const publicationFilter = this.publicationFilter();

          return this.service.list({
            search: this.search.value,
            validationStatus: validationFilter === 'all' ? null : validationFilter,
            published: publicationFilter === 'all'
              ? null
              : publicationFilter === 'published',
            page: this.page(),
            pageSize: this.pageSize,
          }).pipe(
            tap((result) => this.applyList(result)),
            catchError((error: HttpErrorResponse) => {
              this.reports.set([]);
              this.totalItems.set(0);
              this.selectedId.set(null);
              this.detail.set(null);
              this.detailRequests.next('');
              this.listError.set(this.errorMessage(error, 'Não foi possível carregar os relatórios.'));
              return EMPTY;
            }),
            finalize(() => this.listLoading.set(false)),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe();

    this.detailRequests
      .pipe(
        switchMap((id) => {
          if (!id) {
            this.detail.set(null);
            this.detailLoading.set(false);
            return EMPTY;
          }

          this.detailLoading.set(true);
          this.detailError.set(null);
          this.validationConflict.set(false);
          this.validationError.set(null);
          this.pdfError.set(null);
          this.publicationConflict.set(false);
          this.publicationError.set(null);

          return this.service.get(id).pipe(
            tap((report) => this.detail.set(report)),
            catchError((error: HttpErrorResponse) => {
              this.detail.set(null);
              this.detailError.set(this.errorMessage(error, 'Não foi possível carregar este relatório.'));
              return EMPTY;
            }),
            finalize(() => this.detailLoading.set(false)),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe();
  }

  selectReport(report: ReportSummary): void {
    if (report.id === this.selectedId()) {
      if (this.routeReportId() !== report.id) {
        void this.router.navigate(['/tecnico/relatorios', report.id]);
      }
      return;
    }
    this.selectedId.set(report.id);
    this.detail.set(null);
    this.validationMessage.set(null);
    this.validationError.set(null);
    this.publicationMessage.set(null);
    this.detailRequests.next(report.id);
    void this.router.navigate(['/tecnico/relatorios', report.id]);
  }

  setValidationFilter(value: string): void {
    this.validationFilter.set(value as ValidationFilter);
    this.page.set(1);
    this.listRequests.next();
  }

  setPublicationFilter(value: string): void {
    this.publicationFilter.set(value as PublicationFilter);
    this.page.set(1);
    this.listRequests.next();
  }

  previousPage(): void {
    if (this.page() <= 1) return;
    this.page.update((current) => current - 1);
    this.listRequests.next();
  }

  nextPage(): void {
    if (this.page() >= this.pageCount()) return;
    this.page.update((current) => current + 1);
    this.listRequests.next();
  }

  retryList(): void {
    this.listRequests.next();
  }

  retryDetail(): void {
    const id = this.selectedId();
    if (id) this.detailRequests.next(id);
  }

  validateReport(): void {
    const report = this.detail();
    if (!report || report.validationStatus !== 'pending-validation' || this.saving()) return;

    this.saving.set(true);
    this.validationConflict.set(false);
    this.validationError.set(null);
    this.validationMessage.set(null);

    this.service.validate(report.id, report.version, report.contentFingerprint)
      .pipe(
        tap(() => {
          if (this.selectedId() === report.id) {
            this.validationMessage.set('Relatório validado.');
            this.detailRequests.next(report.id);
          }
          this.listRequests.next();
        }),
        catchError((error: HttpErrorResponse) => {
          if (this.selectedId() !== report.id) return EMPTY;
          if (error.status === 409) {
            this.validationConflict.set(true);
          } else {
            this.validationError.set(
              this.errorMessage(error, 'Não foi possível validar o relatório.'),
            );
          }
          return EMPTY;
        }),
        finalize(() => this.saving.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe();
  }

  publishReport(): void {
    const report = this.detail();
    if (!report || report.validationStatus !== 'validated' || report.published ||
        !report.pdfAvailable || this.publishing()) return;

    this.publishing.set(true);
    this.publicationConflict.set(false);
    this.publicationError.set(null);
    this.publicationMessage.set(null);

    this.service.publish(report.id, report.version)
      .pipe(
        tap(() => {
          if (this.selectedId() === report.id) {
            this.publicationMessage.set('Relatório publicado para o cliente. O status técnico do ensaio continua manual.');
            this.detailRequests.next(report.id);
          }
          this.listRequests.next();
        }),
        catchError((error: HttpErrorResponse) => {
          if (this.selectedId() !== report.id) return EMPTY;
          if (error.status === 409) {
            this.publicationConflict.set(true);
          } else {
            this.publicationError.set(
              this.errorMessage(error, 'Não foi possível publicar o relatório.'),
            );
          }
          return EMPTY;
        }),
        finalize(() => this.publishing.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe();
  }

  openPdf(): void {
    this.retrievePdf('open');
  }

  downloadPdf(): void {
    this.retrievePdf('download');
  }

  statusLabel = validationStatusLabel;
  statusClass = validationStatusClass;
  publicationLabel = publicationStatusLabel;
  publicationClass = publicationStatusClass;

  private applyList(result: Page<ReportSummary>): void {
    this.reports.set(result.items);
    this.totalItems.set(result.totalItems);

    const routeId = this.routeReportId();
    if (routeId) {
      const selectionChanged = this.selectedId() !== routeId;
      if (selectionChanged) {
        this.detail.set(null);
        this.publicationMessage.set(null);
      }
      this.selectedId.set(routeId);
      if (selectionChanged || (this.detail()?.id !== routeId && !this.detailLoading())) {
        this.detailRequests.next(routeId);
      }
      return;
    }

    if (!result.items.length) {
      this.selectedId.set(null);
      this.detail.set(null);
      this.detailRequests.next('');
      return;
    }

    const current = result.items.find((report) => report.id === this.selectedId());
    const selected = current ?? result.items[0];
    if (selected.id !== this.selectedId()) {
      this.selectedId.set(selected.id);
      this.validationMessage.set(null);
      this.detail.set(null);
      this.detailRequests.next(selected.id);
    } else if (!this.detail() && !this.detailLoading()) {
      this.detailRequests.next(selected.id);
    }
  }

  private retrievePdf(mode: 'open' | 'download'): void {
    const report = this.detail();
    if (!report || !report.pdfAvailable || this.pdfLoading()) return;

    const target = mode === 'open' ? window.open('about:blank', '_blank') : null;
    if (mode === 'open' && !target) {
      this.pdfError.set('O navegador bloqueou a abertura do PDF. Permita pop-ups para este portal.');
      return;
    }
    if (target) target.opener = null;

    this.pdfLoading.set(true);
    this.pdfError.set(null);
    this.service.pdf(report.id)
      .pipe(
        tap((response) => {
          const body = response.body;
          const contentType = response.headers.get('Content-Type')?.split(';')[0].trim().toLowerCase();
          const blobType = body?.type.toLowerCase();
          if (
            !body ||
            body.size === 0 ||
            (contentType ?? blobType) !== 'application/pdf'
          ) {
            target?.close();
            this.pdfError.set('A API não retornou um PDF válido. Tente novamente.');
            return;
          }

          const pdf = new Blob([body], { type: 'application/pdf' });
          const objectUrl = URL.createObjectURL(pdf);
          const filename = this.pdfFilename(response.headers.get('Content-Disposition'), report);

          if (target) {
            target.location.href = objectUrl;
          } else {
            const link = document.createElement('a');
            link.href = objectUrl;
            link.download = filename;
            link.hidden = true;
            document.body.appendChild(link);
            link.click();
            link.remove();
          }

          window.setTimeout(() => URL.revokeObjectURL(objectUrl), 60_000);
        }),
        catchError((error: HttpErrorResponse) => {
          target?.close();
          this.pdfError.set(this.errorMessage(error, 'Não foi possível obter o PDF do relatório.'));
          return EMPTY;
        }),
        finalize(() => this.pdfLoading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe();
  }

  private pdfFilename(header: string | null, report: ReportSummary): string {
    const filename = /filename\*?=(?:UTF-8''|)?"?([^";]+)/i.exec(header ?? '')?.[1];
    if (!filename) {
      const suffix = report.validationStatus === 'validated' ? '' : '-rascunho';
      return `relatorio-${report.id}${suffix}.pdf`;
    }

    try {
      return decodeURIComponent(filename.trim());
    } catch {
      return filename.trim();
    }
  }

  private errorMessage(error: HttpErrorResponse, fallback: string): string {
    if (error.status === 401) return 'Sua sessão expirou. Entre novamente para continuar.';
    if (error.status === 403) return 'Seu perfil não tem acesso a esta operação.';
    if (error.status === 404) return 'O relatório não está mais disponível.';
    return fallback;
  }
}
