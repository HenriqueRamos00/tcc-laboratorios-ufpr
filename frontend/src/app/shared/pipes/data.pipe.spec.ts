import { DataPipe } from './data.pipe';

describe('DataPipe', () => {
  const pipe = new DataPipe();

  it('não recua um dia por causa do fuso do navegador numa data sem hora', () => {
    expect(pipe.transform('2026-07-06')).toBe('06/07/2026');
  });

  it('devolve o texto como veio quando ele não é uma data', () => {
    expect(pipe.transform('sem data')).toBe('sem data');
  });

  it('formata data com hora no fuso de quem lê, em dia/mês/ano', () => {
    const momento = new Date(2026, 2, 13, 10, 16);

    expect(pipe.transform(momento.toISOString())).toBe('13/03/2026');
    expect(pipe.transform(momento)).toBe('13/03/2026');
  });

  it('ausência vira texto vazio', () => {
    expect(pipe.transform(null)).toBe('');
    expect(pipe.transform(undefined)).toBe('');
    expect(pipe.transform('')).toBe('');
  });
});
