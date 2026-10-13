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
