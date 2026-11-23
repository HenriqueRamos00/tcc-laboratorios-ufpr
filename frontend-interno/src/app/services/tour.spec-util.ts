export async function ate(condicao: () => boolean, descricao: string): Promise<void> {
  const limite = Date.now() + 3000;
  while (!condicao()) {
    if (Date.now() > limite) throw new Error(`Não aconteceu a tempo: ${descricao}`);
    await new Promise((resolve) => setTimeout(resolve, 20));
  }
}

export function balao(): HTMLElement | null {
  return document.querySelector<HTMLElement>('.driver-popover');
}

export function tituloDoBalao(): string {
  return balao()?.querySelector('.driver-popover-title')?.textContent ?? '';
}

export function progressoDoBalao(): string {
  return balao()?.querySelector('.driver-popover-progress-text')?.textContent ?? '';
}

export function botao(classe: string): HTMLButtonElement {
  const elemento = balao()?.querySelector<HTMLButtonElement>(`.${classe}`);
  if (!elemento) throw new Error(`Botão ausente no balão: ${classe}`);
  return elemento;
}

export async function mostrando(titulo: string): Promise<void> {
  await ate(() => tituloDoBalao() === titulo, `balão com o título "${titulo}"`);
}
