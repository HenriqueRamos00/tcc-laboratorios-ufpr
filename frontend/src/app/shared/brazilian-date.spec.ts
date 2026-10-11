import { readableDate } from './brazilian-date';

describe('readableDate', () => {
  // `new Date('2026-07-06')` nasce à meia-noite UTC, e formatar isso no fuso de
  // Brasília devolveria o dia anterior. Um laudo que muda de dia conforme o
  // navegador de quem abre não é laudo.
  it('não recua um dia por causa do fuso do navegador', () => {
    expect(readableDate('2026-07-06')).toBe('06/07/2026');
  });

  // `Intl` lança `RangeError` numa data inválida, e isso derrubava a tabela
  // inteira: uma coleta com data torta apagava também as coletas corretas.
  it('devolve o texto como veio quando ele não é uma data', () => {
    expect(readableDate('sem data')).toBe('sem data');
  });
});
