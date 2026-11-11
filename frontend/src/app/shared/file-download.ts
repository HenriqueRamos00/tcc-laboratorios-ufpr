import type { HttpResponse } from '@angular/common/http';

export interface DownloadedFile {
  readonly content: Blob;
  readonly name: string | null;
}

export function fileNameFromContentDisposition(header: string | null): string | null {
  if (!header) return null;
  const encoded = /filename\*\s*=\s*(?:UTF-8|utf-8)?''([^;]+)/i.exec(header);
  if (encoded) {
    try {
      return decodeURIComponent(encoded[1].trim().replace(/^"|"$/g, '')) || null;
    } catch {
      return null;
    }
  }
  const plain = /filename\s*=\s*("([^"]*)"|[^;]+)/i.exec(header);
  const name = (plain?.[2] ?? plain?.[1] ?? '').trim();
  return name || null;
}

export function toDownloadedFile(response: HttpResponse<Blob>): DownloadedFile {
  return {
    content: response.body ?? new Blob([], { type: 'application/pdf' }),
    name: fileNameFromContentDisposition(response.headers.get('Content-Disposition')),
  };
}

/**
 * Entrega ao navegador um arquivo que já está em memória, como se o cliente
 * tivesse clicado num link para ele.
 *
 * O navegador ainda resolve o endereço depois do clique, então a revogação
 * espera a próxima volta do laço de eventos: revogar na mesma volta cancela o
 * download antes de ele começar. Sem revogar, o Blob fica preso em memória até
 * a aba fechar.
 */
export function handFileToTheBrowser(file: Blob, name: string): void {
  const address = URL.createObjectURL(file);
  const anchor = document.createElement('a');
  anchor.href = address;
  anchor.download = name;
  anchor.click();
  setTimeout(() => URL.revokeObjectURL(address));
}
