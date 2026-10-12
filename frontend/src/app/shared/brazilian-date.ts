// A data chega do AutoLAB no formato ISO (2026-07-06) e o cliente lê a data como
// ela aparece no laudo impresso, em dia/mês/ano.
//
// O fuso fica fixo em UTC de propósito: `new Date('2026-07-06')` nasce à
// meia-noite UTC, e formatar isso no fuso de Brasília devolveria 05/07/2026. Um
// laudo que muda de dia conforme o navegador não é laudo.
const BRAZILIAN_DATE = new Intl.DateTimeFormat('pt-BR', { timeZone: 'UTC' });

/**
 * Texto que não é uma data volta como veio, em vez de derrubar a tela. `Intl`
 * lança `RangeError` numa data inválida, e esta função é chamada uma vez por
 * coluna do laudo: uma data torta numa coleta antiga apagaria a tabela inteira,
 * inclusive as coletas que estão certas. Mostrar o texto como o laboratório
 * escreveu é a mesma regra que vale para a célula de resultado ilegível.
 */
export function readableDate(iso: string): string {
  const moment = new Date(iso);

  return Number.isNaN(moment.getTime()) ? iso : BRAZILIAN_DATE.format(moment);
}
