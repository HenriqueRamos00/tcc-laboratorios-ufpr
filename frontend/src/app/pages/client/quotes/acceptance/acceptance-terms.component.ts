import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  input,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';

import { TextFieldComponent } from '@shared/components/text-field/text-field.component';
import { PageHeaderComponent } from '@shared/components/page-header/page-header.component';
import { SampleShippingNoticeComponent } from '@shared/components/sample-shipping-notice/sample-shipping-notice.component';

import type { AcceptanceTerms } from '@/app/model/acceptance';
import { AppDialogService } from '@/app/services/app-dialog.service';
import { QuoteDecisionService } from '@/app/services/quote-decision.service';

import {
  ConfirmAcceptanceDialogComponent,
  type ConfirmAcceptanceDialogData,
} from './confirm-acceptance-dialog.component';

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
    fax: [''],
    invoiceEmails: [''],
  });
}

@Component({
  selector: 'app-termo-de-aceite',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    PageHeaderComponent,
    TextFieldComponent,
    SampleShippingNoticeComponent,
  ],
  templateUrl: './acceptance-terms.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AcceptanceTermsComponent {
  readonly id = input.required<string>();

  private readonly fb = inject(FormBuilder);
  private readonly decisions = inject(QuoteDecisionService);
  private readonly dialogs = inject(AppDialogService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly sending = signal(false);

  // Array literal no template cria identidade nova a cada ciclo e suja o
  // PageHeader, que é OnPush, sem nada ter mudado.
  protected readonly breadcrumbs = computed(() => [
    { label: 'Orçamentos', route: '/cliente/quotes' },
    { label: this.id(), route: `/cliente/quotes/${this.id()}` },
    { label: 'Termo de aceite' },
  ]);

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

  constructor() {
    // O e-mail só é obrigatório quando o cliente autoriza o envio de resultados
    // por e-mail (HU-09, critério 4).
    this.form.controls.approval.controls.allowsResultsByEmail.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((allows) => {
        const field = this.form.controls.approval.controls.resultsEmail;
        field.setValidators(allows ? [Validators.required, Validators.email] : [Validators.email]);
        field.updateValueAndValidity();
      });
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

  private send(): void {
    const raw = this.form.getRawValue();
    const terms: AcceptanceTerms = {
      proposalId: this.id(),
      billing: raw.billing,
      approval: raw.approval,
    };

    this.sending.set(true);
    this.decisions
      .aceitar(terms)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.sending.set(false);
          this.snackBar.open('Aceite enviado com sucesso', 'Fechar', { duration: 5000 });
          void this.router.navigate(['/cliente/quotes']);
        },
        error: () => {
          this.sending.set(false);
          this.snackBar.open('Não foi possível enviar o aceite no momento', 'Fechar', {
            duration: 5000,
          });
        },
      });
  }
}
