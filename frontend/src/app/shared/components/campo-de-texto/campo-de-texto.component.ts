import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { ReactiveFormsModule, type FormControl } from '@angular/forms';
import { switchMap } from 'rxjs';

// Recebe o controle e não o caminho em string: `formulario.get('a.b')` devolve
// `AbstractControl | null` e joga fora a tipagem do `fb.nonNullable.group()`.
//
// A ligação do erro ao input é o que o MatFormField faria, e que não dá para
// usar aqui sem mudar o desenho da tela.

let proximoId = 0;

@Component({
  selector: 'app-campo-de-texto',
  standalone: true,
  imports: [ReactiveFormsModule],
  host: { class: 'block' },
  template: `
    <label class="block">
      <span class="text-sm text-lactec-ink-soft">{{ rotulo() }}</span>
      <input
        [type]="tipo()"
        [formControl]="controle()"
        class="campo"
        [class.uppercase]="caixaAlta()"
        [attr.inputmode]="inputmode()"
        [attr.maxlength]="maxlength()"
        [attr.aria-invalid]="invalido() ? 'true' : null"
        [attr.aria-describedby]="invalido() ? idDoErro : null"
      />
      @if (invalido()) {
        <span class="erro" [id]="idDoErro" role="alert">{{ mensagem() }}</span>
      }
    </label>
  `,
  styles: `
    .campo {
      margin-top: 0.25rem;
      display: block;
      height: 2.75rem;
      width: 100%;
      border-radius: 0.5rem;
      border: 1px solid var(--color-lactec-line);
      background: var(--color-lactec-paper);
      padding: 0 0.75rem;
      font-size: 0.875rem;
      color: var(--color-lactec-ink);
    }

    .campo:focus {
      border-color: var(--color-lactec-primary);
      outline: none;
    }

    .campo:disabled {
      background: var(--color-lactec-surface);
      color: var(--color-lactec-muted);
    }

    .erro {
      margin-top: 0.25rem;
      display: block;
      font-size: 0.75rem;
      color: var(--color-lactec-danger);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CampoDeTextoComponent {
  readonly controle = input.required<FormControl<string>>();
  readonly rotulo = input.required<string>();
  readonly tipo = input<'text' | 'tel' | 'email' | 'date'>('text');
  readonly inputmode = input<string>();
  readonly maxlength = input<number>();
  readonly caixaAlta = input(false);

  /** Precisa ser único: `aria-describedby` aponta para um id, não para uma classe. */
  protected readonly idDoErro = `erro-do-campo-${proximoId++}`;

  // Sem escutar `events`, um componente OnPush não redesenha quando o pai marca
  // o formulário inteiro como touched.
  private readonly eventos = toSignal(
    toObservable(this.controle).pipe(switchMap((controle) => controle.events)),
  );

  protected readonly invalido = computed(() => {
    this.eventos();
    const controle = this.controle();
    return controle.invalid && (controle.dirty || controle.touched);
  });

  protected readonly mensagem = computed(() => {
    this.eventos();
    return this.controle().hasError('email') ? 'Informe um e-mail válido' : 'Campo obrigatório';
  });
}
