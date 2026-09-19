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

import { CampoDeTextoComponent } from '@shared/components/campo-de-texto/campo-de-texto.component';
import { PageHeaderComponent } from '@shared/components/page-header/page-header.component';

import type { TermoDeAceite } from '@/app/model/acceptance';
import { QuoteDecisionService } from '@/app/services/quote-decision.service';

// Anexo II da proposta (HU-09). Os campos são os do formulário impresso, para
// que o cliente reconheça o documento que já recebe por e-mail hoje.
function grupoDeEndereco(fb: FormBuilder, obrigatorio: boolean) {
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
  imports: [ReactiveFormsModule, PageHeaderComponent, CampoDeTextoComponent],
  templateUrl: './termo-de-aceite.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TermoDeAceiteComponent {
  readonly id = input.required<string>();

  private readonly fb = inject(FormBuilder);
  private readonly decisao = inject(QuoteDecisionService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly enviando = signal(false);

  // Array literal no template cria identidade nova a cada ciclo e suja o
  // PageHeader, que é OnPush, sem nada ter mudado.
  protected readonly migalhas = computed(() => [
    { rotulo: 'Orçamentos', rota: '/cliente/orcamentos' },
    { rotulo: this.id(), rota: `/cliente/orcamentos/${this.id()}` },
    { rotulo: 'Termo de aceite' },
  ]);

  protected readonly formulario = this.fb.group({
    cobranca: grupoDeEndereco(this.fb, true),
    mesmoEnderecoDaCobranca: this.fb.nonNullable.control(true),
    envioDoRelatorio: grupoDeEndereco(this.fb, false),
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
    const cobranca = this.formulario.controls.cobranca;
    const mesmoEndereco = this.formulario.controls.mesmoEnderecoDaCobranca;
    const envio = this.formulario.controls.envioDoRelatorio;

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
    this.formulario.controls.aprovacao.controls.autorizaEnvioPorEmail.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((autoriza) => {
        const campo = this.formulario.controls.aprovacao.controls.emailParaResultados;
        campo.setValidators(
          autoriza ? [Validators.required, Validators.email] : [Validators.email],
        );
        campo.updateValueAndValidity();
      });
  }

  protected enviar(): void {
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      return;
    }

    const bruto = this.formulario.getRawValue();
    const termo: TermoDeAceite = {
      propostaId: this.id(),
      cobranca: bruto.cobranca,
      // getRawValue() inclui o grupo desabilitado quando o cliente marcou
      // "mesmo endereço da cobrança", então o espelho chega preenchido.
      envioDoRelatorio: bruto.envioDoRelatorio,
      mesmoEnderecoDaCobranca: bruto.mesmoEnderecoDaCobranca,
      aprovacao: bruto.aprovacao,
    };

    this.enviando.set(true);
    this.decisao
      .aceitar(termo)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.enviando.set(false);
          this.snackBar.open('Aceite enviado com sucesso', 'Fechar', { duration: 5000 });
          void this.router.navigate(['/cliente/orcamentos']);
        },
        error: () => {
          this.enviando.set(false);
          this.snackBar.open('Não foi possível enviar o aceite no momento', 'Fechar', {
            duration: 5000,
          });
        },
      });
  }
}
