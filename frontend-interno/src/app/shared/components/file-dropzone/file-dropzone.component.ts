import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

const MEGABYTE = 1024 * 1024;

@Component({
  selector: 'app-file-dropzone',
  standalone: true,
  imports: [MatIconModule],
  host: { class: 'block' },
  template: `
    <div
      class="flex flex-col items-center justify-center gap-3 rounded-lg border-2 border-dashed
             px-6 py-8 text-center transition-colors"
      [class.border-lactec-primary]="arrastando()"
      [class.border-lactec-line]="!arrastando()"
      [style.background]="arrastando() ? 'color-mix(in srgb, var(--color-lactec-primary) 6%, var(--color-lactec-surface))' : 'var(--color-lactec-surface)'"
      (dragover)="aoArrastarSobre($event)"
      (dragleave)="aoSairDaArea($event)"
      (drop)="aoSoltar($event)"
    >
      <mat-icon class="icone-36 text-lactec-nav" aria-hidden="true">cloud_upload</mat-icon>

      @if (arquivo(); as escolhido) {
        <p class="m-0 max-w-full break-words text-sm font-medium text-lactec-ink [overflow-wrap:anywhere]">
          {{ escolhido.name }}
        </p>
        <button
          type="button"
          class="text-sm text-lactec-primary underline focus-visible:rounded-sm focus-visible:outline focus-visible:outline-2 focus-visible:outline-lactec-primary"
          [disabled]="disabled()"
          (click)="limpar()"
        >
          Trocar arquivo
        </button>
      } @else {
        <p class="m-0 text-sm text-lactec-ink-soft">
          Arraste um arquivo aqui ou
          <label class="cursor-pointer text-lactec-primary underline focus-within:rounded-sm focus-within:outline focus-within:outline-2 focus-within:outline-lactec-primary">
            selecione no dispositivo
            <input
              type="file"
              class="sr-only"
              [disabled]="disabled()"
              [attr.accept]="extensoesAceitas()"
              (change)="aoEscolher($event)"
            />
          </label>
        </p>
      }
      @if (disabled()) {
        <p class="m-0 text-xs text-lactec-muted">Aguarde o salvamento terminar para trocar o arquivo.</p>
      }
    </div>

    <p class="mt-2 text-center text-xs text-lactec-muted">
      Formato aceito: {{ formatosLegiveis() }}. Tamanho máximo: {{ tamanhoMaximoMb() }} MB.
    </p>

    @if (erro(); as mensagem) {
      <p class="mt-1 text-center text-xs text-lactec-danger" role="alert">{{ mensagem }}</p>
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FileDropzoneComponent {
  readonly formatos = input<readonly string[]>(['PDF']);
  readonly tamanhoMaximoMb = input(50);
  readonly disabled = input(false);
  readonly arquivoEscolhido = output<File | null>();
  readonly erroAlterado = output<string | null>();

  protected readonly arrastando = signal(false);
  protected readonly arquivo = signal<File | null>(null);
  protected readonly erro = signal<string | null>(null);

  protected readonly formatosLegiveis = computed(() => this.formatos().join(', '));
  protected readonly extensoesAceitas = computed(() =>
    this.formatos().map((formato) => `.${formato.toLowerCase()}`).join(','),
  );

  protected aoArrastarSobre(evento: DragEvent): void {
    if (this.disabled()) return;
    evento.preventDefault();
    this.arrastando.set(true);
  }

  protected aoSoltar(evento: DragEvent): void {
    evento.preventDefault();
    this.arrastando.set(false);
    if (!this.disabled()) this.aceitar(evento.dataTransfer?.files?.[0] ?? null);
  }

  protected aoSairDaArea(evento: DragEvent): void {
    const area = evento.currentTarget as HTMLElement;
    const destino = evento.relatedTarget as Node | null;
    if (destino && area.contains(destino)) return;
    this.arrastando.set(false);
  }

  protected aoEscolher(evento: Event): void {
    const entrada = evento.target as HTMLInputElement;
    if (!this.disabled()) this.aceitar(entrada.files?.[0] ?? null);
    entrada.value = '';
  }

  protected limpar(): void {
    if (this.disabled()) return;
    this.arquivo.set(null);
    this.erro.set(null);
    this.arquivoEscolhido.emit(null);
    this.erroAlterado.emit(null);
  }

  private aceitar(arquivo: File | null): void {
    if (!arquivo) return;

    const extensao = arquivo.name.split('.').pop()?.toUpperCase() ?? '';
    if (!this.formatos().some((formato) => formato.toUpperCase() === extensao)) {
      this.rejeitar(`Formato não suportado. Envie arquivo ${this.formatosLegiveis()}.`);
      return;
    }
    if (arquivo.size > this.tamanhoMaximoMb() * MEGABYTE) {
      this.rejeitar(`O arquivo passa de ${this.tamanhoMaximoMb()} MB.`);
      return;
    }

    this.erro.set(null);
    this.erroAlterado.emit(null);
    this.arquivo.set(arquivo);
    this.arquivoEscolhido.emit(arquivo);
  }

  private rejeitar(mensagem: string): void {
    this.arquivo.set(null);
    this.erro.set(mensagem);
    this.erroAlterado.emit(mensagem);
    this.arquivoEscolhido.emit(null);
  }
}
