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
             px-6 py-10 text-center transition-colors"
      [class.border-lactec-primary]="arrastando()"
      [class.border-lactec-line]="!arrastando()"
      [style.background]="arrastando() ? 'rgba(24, 165, 184, 0.06)' : 'var(--color-lactec-surface)'"
      (dragover)="aoArrastarSobre($event)"
      (dragleave)="aoSairDaArea($event)"
      (drop)="aoSoltar($event)"
    >
      <mat-icon class="icone-36 text-lactec-nav" aria-hidden="true">cloud_upload</mat-icon>

      @if (arquivo(); as escolhido) {
        <p class="m-0 text-sm font-medium text-lactec-ink">{{ escolhido.name }}</p>
        <button type="button" class="text-sm text-lactec-primary underline" (click)="limpar()">
          Trocar arquivo
        </button>
      } @else {
        <p class="m-0 text-sm text-lactec-ink-soft">
          Arraste seus arquivos aqui ou
          <label class="cursor-pointer text-lactec-primary underline">
            Busque seu arquivo
            <input
              type="file"
              class="sr-only"
              [attr.accept]="extensoesAceitas()"
              (change)="aoEscolher($event)"
            />
          </label>
        </p>
      }
    </div>

    <p class="mt-2 text-center text-xs text-lactec-muted">
      Formatos Suportados: {{ formatosLegiveis() }}. Tamanho máximo: {{ tamanhoMaximoMb() }}MB
    </p>

    @if (erro(); as mensagem) {
      <p class="mt-1 text-center text-xs text-lactec-danger" role="alert">{{ mensagem }}</p>
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FileDropzoneComponent {
  readonly formatos = input<readonly string[]>(['PDF', 'DOC', 'DOCX']);
  readonly tamanhoMaximoMb = input(50);
  readonly arquivoEscolhido = output<File | null>();

  protected readonly arrastando = signal(false);
  protected readonly arquivo = signal<File | null>(null);
  protected readonly erro = signal<string | null>(null);

  protected readonly formatosLegiveis = computed(() => this.formatos().join(', '));
  protected readonly extensoesAceitas = computed(() =>
    this.formatos().map((formato) => `.${formato.toLowerCase()}`).join(','),
  );

  protected aoArrastarSobre(evento: DragEvent): void {
    evento.preventDefault();
    this.arrastando.set(true);
  }

  protected aoSoltar(evento: DragEvent): void {
    evento.preventDefault();
    this.arrastando.set(false);
    this.aceitar(evento.dataTransfer?.files?.[0] ?? null);
  }

  // dragleave dispara também ao entrar num filho; sem o teste, a borda pisca
  // durante o arraste.
  protected aoSairDaArea(evento: DragEvent): void {
    const area = evento.currentTarget as HTMLElement;
    const destino = evento.relatedTarget as Node | null;
    if (destino && area.contains(destino)) return;
    this.arrastando.set(false);
  }

  protected aoEscolher(evento: Event): void {
    const entrada = evento.target as HTMLInputElement;
    this.aceitar(entrada.files?.[0] ?? null);
    // Sem zerar, escolher o mesmo arquivo depois de uma rejeição não dispara
    // change de novo e o usuário fica sem resposta.
    entrada.value = '';
  }

  protected limpar(): void {
    this.arquivo.set(null);
    this.erro.set(null);
    this.arquivoEscolhido.emit(null);
  }

  private aceitar(arquivo: File | null): void {
    if (!arquivo) return;

    const extensao = arquivo.name.split('.').pop()?.toUpperCase() ?? '';
    if (!this.formatos().includes(extensao)) {
      this.erro.set(`Formato não suportado. Envie ${this.formatosLegiveis()}.`);
      return;
    }
    if (arquivo.size > this.tamanhoMaximoMb() * MEGABYTE) {
      this.erro.set(`O arquivo passa de ${this.tamanhoMaximoMb()}MB.`);
      return;
    }

    this.erro.set(null);
    this.arquivo.set(arquivo);
    this.arquivoEscolhido.emit(arquivo);
  }
}
