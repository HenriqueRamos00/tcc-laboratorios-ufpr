import { FIELD_LIMITS } from '@/app/model/field-limits';
import { FIELD_MASKS } from '@/app/model/field-masks';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse, HttpStatusCode } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  computed,
  DestroyRef,
  inject,
  input,
  OnInit,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  FormArray,
  FormBuilder,
  FormControl,
  ReactiveFormsModule,
  Validators,
  type AbstractControl,
  type ValidationErrors,
} from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';
import { debounceTime, map, Observable } from 'rxjs';

import type { ConfirmsLeaving } from '@core/guards/unsent-changes.guard';
import { TextFieldComponent } from '@shared/components/text-field/text-field.component';
import { PageHeaderComponent } from '@shared/components/page-header/page-header.component';
import { SampleShippingNoticeComponent } from '@shared/components/sample-shipping-notice/sample-shipping-notice.component';

import {
  INVOICE_EMAIL_COUNT,
  type AcceptanceFormValue,
  type AcceptanceTerms,
} from '@/app/model/acceptance';
import {
  ACCEPTANCE_DRAFT_VALIDITY_DAYS,
  AcceptanceDraftService,
  type AcceptanceDraft,
} from '@/app/services/acceptance-draft.service';
import { AppDialogService } from '@/app/services/app-dialog.service';
import { LoginService } from '@/app/services/login.service';
import { QuoteDecisionService } from '@/app/services/quote-decision.service';

import {
  ConfirmAcceptanceDialogComponent,
  type ConfirmAcceptanceDialogData,
} from './confirm-acceptance-dialog.component';
import {
  LeaveAcceptanceDialogComponent,
  type LeaveAcceptanceChoice,
  type LeaveAcceptanceDialogData,
} from './leave-acceptance-dialog.component';
import {
  ResumeDraftDialogComponent,
  type ResumeDraftChoice,
  type ResumeDraftDialogData,
} from './resume-draft-dialog.component';

import { registerPageTour } from '@/app/services/tour.service';
import { acceptanceTermsTour } from './acceptance-terms.tour';

export const DRAFT_SAVE_DELAY_MS = 600;

function comparableEmail(value: string): string {
  return value.trim().toLowerCase();
}

function notRepeatedBefore(control: AbstractControl<string>): ValidationErrors | null {
  const email = comparableEmail(control.value);
  const siblings = control.parent instanceof FormArray ? control.parent.controls : [];
  const first = siblings.find((sibling) => comparableEmail(sibling.value) === email);
  return email && first && first !== control ? { repetido: true } : null;
}

function invoiceEmailControl(fb: FormBuilder): FormControl<string> {
  return fb.nonNullable.control('', [Validators.required, Validators.email, notRepeatedBefore]);
}

// Anexo II da proposta (HU-09). Os campos são os do formulário impresso, para
// que o cliente reconheça o documento que já recebe por e-mail hoje. A seção do
// endereço de envio do relatório saiu junto com a entrega em papel: o laudo é
// publicado no portal, e endereço agora só existe para a cobrança.
function billingGroup(fb: FormBuilder) {
  const required = [Validators.required];
  return fb.nonNullable.group({
    company: ['', required],
    cnpj: ['', required],
    stateRegistration: [''],
    street: ['', required],
    city: ['', required],
    state: ['', required],
    district: [''],
    postalCode: ['', required],
    contact: [''],
    phone: [''],
    invoiceEmails: fb.array(
      Array.from({ length: INVOICE_EMAIL_COUNT.min }, () => invoiceEmailControl(fb)),
    ),
  });
}

@Component({
  selector: 'app-termo-de-aceite',
  standalone: true,
  imports: [
    DatePipe,
    ReactiveFormsModule,
    PageHeaderComponent,
    TextFieldComponent,
    SampleShippingNoticeComponent,
  ],
  templateUrl: './acceptance-terms.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '(window:beforeunload)': 'warnBeforeUnload($event)',
    '(window:pagehide)': 'saveDraft()',
  },
})
export class AcceptanceTermsComponent implements OnInit, ConfirmsLeaving {
  readonly id = input.required<string>();

  private readonly fb = inject(FormBuilder);
  private readonly decisions = inject(QuoteDecisionService);
  private readonly drafts = inject(AcceptanceDraftService);
  private readonly session = inject(LoginService);
  private readonly dialogs = inject(AppDialogService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly changes = inject(ChangeDetectorRef);

  protected readonly sending = signal(false);
  protected readonly draftSavedAt = signal<Date | null>(null);
  protected readonly draftUnavailable = signal(false);

  // Array literal no template cria identidade nova a cada ciclo e suja o
  // PageHeader, que é OnPush, sem nada ter mudado.
  protected readonly breadcrumbs = computed(() => [
    { label: 'Orçamentos', route: '/cliente/quotes' },
    { label: this.id(), route: `/cliente/quotes/${this.id()}` },
    { label: 'Termo de aceite' },
  ]);

  protected readonly limits = FIELD_LIMITS;
  protected readonly masks = FIELD_MASKS;
  protected readonly invoiceEmailCount = INVOICE_EMAIL_COUNT;
  protected readonly draftValidityDays = ACCEPTANCE_DRAFT_VALIDITY_DAYS;

  protected readonly form = this.fb.group({
    billing: billingGroup(this.fb),
    approval: this.fb.nonNullable.group({
      requesterName: ['', Validators.required],
      responsibleName: ['', Validators.required],
      jobTitle: ['', Validators.required],
      department: [''],
      date: ['', Validators.required],
      allowsResultsByEmail: [true],
      resultsEmail: ['', [Validators.required, Validators.email]],
    }),
  });

  private readonly invoiceEmails = this.form.controls.billing.controls.invoiceEmails;
  private readonly blank = this.snapshot();
  private baseline = this.blank;
  private draftsEnabled = false;
  private finished = false;

  constructor() {
    registerPageTour(acceptanceTermsTour);

    // O e-mail só é obrigatório quando o cliente autoriza o envio de resultados
    // por e-mail (HU-09, critério 4).
    this.form.controls.approval.controls.allowsResultsByEmail.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((allows) => {
        const field = this.form.controls.approval.controls.resultsEmail;
        field.setValidators(allows ? [Validators.required, Validators.email] : [Validators.email]);
        field.updateValueAndValidity();
      });

    this.invoiceEmails.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.recheckRepeatedInvoiceEmails());

    this.form.valueChanges
      .pipe(debounceTime(DRAFT_SAVE_DELAY_MS), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.saveDraft());

    this.destroyRef.onDestroy(() => this.saveDraft());
  }

  ngOnInit(): void {
    this.offerStoredDraft();
  }

  hasUnsentChanges(): boolean {
    return !this.finished && this.snapshot() !== this.baseline;
  }

  confirmLeaving(): Observable<boolean> {
    const data: LeaveAcceptanceDialogData = {
      draftSaved: this.saveDraft(),
      validityDays: ACCEPTANCE_DRAFT_VALIDITY_DAYS,
    };

    return this.dialogs
      .open<LeaveAcceptanceDialogComponent, LeaveAcceptanceDialogData, LeaveAcceptanceChoice>(
        LeaveAcceptanceDialogComponent,
        data,
        'Sair do termo de aceite',
      )
      .afterClosed()
      .pipe(
        map((choice) => {
          if (choice === 'discard') {
            this.finished = true;
            this.drafts.discard(this.id());
          }
          return choice !== undefined;
        }),
      );
  }

  protected addInvoiceEmail(): void {
    if (this.invoiceEmails.length < INVOICE_EMAIL_COUNT.max) {
      this.invoiceEmails.push(invoiceEmailControl(this.fb));
    }
  }

  protected removeInvoiceEmail(index: number): void {
    if (this.invoiceEmails.length > INVOICE_EMAIL_COUNT.min) this.invoiceEmails.removeAt(index);
  }

  // O envio passa por uma confirmação porque o aceite põe o orçamento em
  // execução: depois dele, corrigir um dado do termo depende da equipe.
  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.dialogs
      .open<ConfirmAcceptanceDialogComponent, ConfirmAcceptanceDialogData, boolean>(
        ConfirmAcceptanceDialogComponent,
        { proposalCode: this.id() },
        'Confirmar envio do aceite',
      )
      .afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((confirmed) => {
        if (confirmed) this.send();
      });
  }

  protected warnBeforeUnload(event: BeforeUnloadEvent): void {
    const kept = this.saveDraft() && this.drafts.outlivesTheTab();
    if (!kept && this.hasUnsentChanges()) event.preventDefault();
  }

  protected saveDraft(): boolean {
    if (!this.draftsEnabled || this.finished) return true;

    if (this.snapshot() === this.blank) {
      this.drafts.discard(this.id());
      this.draftSavedAt.set(null);
      this.draftUnavailable.set(false);
      return true;
    }

    const savedAt = this.drafts.save(this.owner(), this.id(), this.form.getRawValue());
    this.draftSavedAt.set(savedAt);
    this.draftUnavailable.set(savedAt === null);
    return savedAt !== null;
  }

  private offerStoredDraft(): void {
    const draft = this.drafts.read(this.owner(), this.id());
    if (!draft) {
      this.draftsEnabled = true;
      return;
    }

    this.dialogs
      .open<ResumeDraftDialogComponent, ResumeDraftDialogData, ResumeDraftChoice>(
        ResumeDraftDialogComponent,
        { proposalCode: this.id(), savedAt: draft.savedAt },
        'Continuar o termo de aceite de onde parou',
      )
      .afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((choice) => {
        if (choice === 'start-over') this.drafts.discard(this.id());
        else this.restore(draft);
        this.draftsEnabled = true;
      });
  }

  private restore(draft: AcceptanceDraft): void {
    const emails = draft.terms.billing.invoiceEmails.length;
    while (this.invoiceEmails.length < emails) {
      this.invoiceEmails.push(invoiceEmailControl(this.fb), { emitEvent: false });
    }
    while (this.invoiceEmails.length > emails) {
      this.invoiceEmails.removeAt(this.invoiceEmails.length - 1, { emitEvent: false });
    }

    this.form.patchValue(draft.terms);
    this.baseline = this.snapshot();
    this.draftSavedAt.set(draft.savedAt);
    this.changes.markForCheck();
  }

  private recheckRepeatedInvoiceEmails(): void {
    for (const email of this.invoiceEmails.controls) {
      email.updateValueAndValidity({ onlySelf: true });
    }
    this.invoiceEmails.updateValueAndValidity({ emitEvent: false });
  }

  private owner(): string | null {
    return this.session.currentIdentity()?.id ?? null;
  }

  private snapshot(): string {
    return JSON.stringify(this.form.getRawValue());
  }

  private send(): void {
    const value: AcceptanceFormValue = this.form.getRawValue();
    const terms: AcceptanceTerms = { proposalId: this.id(), ...value };

    this.sending.set(true);
    this.decisions
      .aceitar(terms)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.finished = true;
          this.sending.set(false);
          this.snackBar.open('Aceite enviado com sucesso', 'Fechar', { duration: 5000 });
          void this.router.navigate(['/cliente/quotes']);
        },
        error: (failure: unknown) => {
          this.sending.set(false);
          if (failure instanceof HttpErrorResponse && failure.status === HttpStatusCode.Conflict) {
            this.finished = true;
            this.drafts.discard(this.id());
            this.snackBar.open('Este orçamento já tem uma decisão registrada', 'Fechar', {
              duration: 5000,
            });
            void this.router.navigate(['/cliente/quotes', this.id()]);
            return;
          }
          this.snackBar.open('Não foi possível enviar o aceite no momento', 'Fechar', {
            duration: 5000,
          });
        },
      });
  }
}
