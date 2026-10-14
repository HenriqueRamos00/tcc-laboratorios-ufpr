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
import { combineLatest, startWith } from 'rxjs';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';

import { TextFieldComponent } from '@shared/components/text-field/text-field.component';
import { PageHeaderComponent } from '@shared/components/page-header/page-header.component';
import { SampleShippingNoticeComponent } from '@shared/components/sample-shipping-notice/sample-shipping-notice.component';

import type { AcceptanceTerms } from '@/app/model/acceptance';
import { QuoteDecisionService } from '@/app/services/quote-decision.service';

// Anexo II da proposta (HU-09). Os campos são os do formulário impresso, para
// que o cliente reconheça o documento que já recebe por e-mail hoje.
function addressGroup(fb: FormBuilder, obrigatorio: boolean) {
  const exigido = obrigatorio ? [Validators.required] : [];
  return fb.nonNullable.group({
    empresa: ['', exigido],
    cnpj: ['', exigido],
    inscricaoEstadual: [''],
    endereco: ['', exigido],
    cidade: ['', exigido],
    uf: ['', exigido],
    bairro: [''],
    cep: ['', exigido],
    contato: [''],
    telefone: [''],
    fax: [''],
    emailsParaNotaFiscal: [''],
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
    cobranca: addressGroup(this.fb, true),
    mesmoEnderecoDaCobranca: this.fb.nonNullable.control(true),
    envioDoRelatorio: addressGroup(this.fb, false),
    aprovacao: this.fb.nonNullable.group({
      nomeDoSolicitante: ['', Validators.required],
      nomeDoResponsavel: ['', Validators.required],
      cargo: ['', Validators.required],
      setor: [''],
      data: ['', Validators.required],
      autorizaEnvioPorEmail: [true],
      emailParaResultados: ['', [Validators.required, Validators.email]],
    }),
  });

  constructor() {
    // FormControl não é signal: um effect lendo `.value` fica sem dependência
    // reativa, roda uma vez e nunca mais reabilita a seção. O combineLatest
    // mantém o espelho vivo enquanto a caixa está marcada.
    const cobranca = this.form.controls.cobranca;
    const mesmoEndereco = this.form.controls.mesmoEnderecoDaCobranca;
    const envio = this.form.controls.envioDoRelatorio;

    combineLatest([
      mesmoEndereco.valueChanges.pipe(startWith(mesmoEndereco.value)),
      cobranca.valueChanges.pipe(startWith(cobranca.getRawValue())),
    ])
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(([espelhar]) => {
        if (espelhar) {
          envio.patchValue(cobranca.getRawValue(), { emitEvent: false });
          if (envio.enabled) envio.disable({ emitEvent: false });
        } else if (envio.disabled) {
          envio.enable({ emitEvent: false });
        }
      });

    // O e-mail só é obrigatório quando o cliente autoriza o envio de resultados
    // por e-mail (HU-09, critério 4).
    this.form.controls.aprovacao.controls.autorizaEnvioPorEmail.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((autoriza) => {
        const campo = this.form.controls.aprovacao.controls.emailParaResultados;
        campo.setValidators(
          autoriza ? [Validators.required, Validators.email] : [Validators.email],
        );
        campo.updateValueAndValidity();
      });
  }

  protected enviar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const bruto = this.form.getRawValue();
    const termo: AcceptanceTerms = {
      proposalId: this.id(),
      cobranca: bruto.cobranca,
      // getRawValue() inclui o grupo desabilitado quando o cliente marcou
      // "mesmo endereço da cobrança", então o espelho chega preenchido.
      envioDoRelatorio: bruto.envioDoRelatorio,
      mesmoEnderecoDaCobranca: bruto.mesmoEnderecoDaCobranca,
      aprovacao: bruto.aprovacao,
    };

    this.sending.set(true);
    this.decisions
      .aceitar(termo)
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
