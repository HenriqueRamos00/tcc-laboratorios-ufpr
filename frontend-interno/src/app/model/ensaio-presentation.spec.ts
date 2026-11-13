import {
  blankMeasurement,
  classificationLabel,
  formatCivilDate,
  isValidQuantityInput,
  processStatusLabel,
  resultFromInput,
  resultToInput,
} from './ensaio-presentation';

describe('Apresentação de ensaios', () => {
  it('preserva a precisão de quantidades e aceita vírgula decimal', () => {
    expect(resultFromInput('0,00000120', 'quantity')).toEqual({
      kind: 'quantity',
      value: '0.00000120',
    });
    expect(resultFromInput('+001,20', 'quantity')).toEqual({
      kind: 'quantity',
      value: '1.20',
    });
    expect(resultToInput({ kind: 'quantity', value: '12.3000' })).toBe(
      '12,3000',
    );
  });
  it('preserva o tipo informado para ND textual, números como texto e quantidades', () => {
    expect(resultFromInput('ND', 'text')).toEqual({ kind: 'text', text: 'ND' });
    expect(resultFromInput('12,30', 'text')).toEqual({ kind: 'text', text: '12,30' });
    expect(resultFromInput('0,01%', 'text')).toEqual({ kind: 'text', text: '0,01%' });
    expect(resultFromInput('12,30', 'quantity')).toEqual({ kind: 'quantity', value: '12.30' });
    expect(resultFromInput('', 'not-detected')).toEqual({ kind: 'not-detected' });
    expect(resultFromInput('', 'not-performed')).toEqual({ kind: 'not-performed' });
    expect(resultToInput({ kind: 'text', text: '  ND\n 12,30 ' })).toBe('  ND\n 12,30 ');
  });
  it('valida a precisão decimal do contrato sem coerção numérica', () => {
    expect(isValidQuantityInput('1234567890123456,12345678')).toBeTrue();
    expect(isValidQuantityInput('12345678901234567')).toBeFalse();
    expect(isValidQuantityInput('1,123456789')).toBeFalse();
    expect(isValidQuantityInput('0,01%')).toBeFalse();
  });
  it('inicia medições de template como não realizadas', () => {
    expect(
      blankMeasurement({
        code: 'h2',
        name: 'Hidrogênio',
        unit: 'ppm',
        method: null,
        referenceLimit: null,
      }).result,
    ).toEqual({ kind: 'not-performed' });
  });
  it('formata a data civil em português sem aplicar deslocamento UTC', () => {
    expect(formatCivilDate('2026-10-26')).toBe('26/10/2026');
  });
  it('separa rótulos de classificação e de estado de processo', () => {
    expect(classificationLabel(null)).toBe('Não classificada');
    expect(processStatusLabel('incomplete')).toBe('Em execução');
  });
});
