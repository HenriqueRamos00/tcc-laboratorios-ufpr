import { resultLabelConformity } from './health';

describe('resultLabelConformity', () => {
  it('lê NORMAL como normal, sem diferenciar maiúsculas nem espaços', () => {
    expect(resultLabelConformity('NORMAL')).toBe('normal');
    expect(resultLabelConformity('Normal')).toBe('normal');
    expect(resultLabelConformity(' normal ')).toBe('normal');
  });

  it('lê qualquer outro rótulo como não conforme', () => {
    expect(resultLabelConformity('Descarga elétrica')).toBe('nao-conforme');
    expect(resultLabelConformity('ALERTA')).toBe('nao-conforme');
  });

  it('não classifica laudo sem rótulo', () => {
    expect(resultLabelConformity(null)).toBeNull();
    expect(resultLabelConformity('  ')).toBeNull();
  });
});
