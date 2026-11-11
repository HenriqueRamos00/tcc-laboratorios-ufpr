import {
  formatReportDate,
  measurementResultLabel,
  reportEventDateLabel,
} from './relatorio-presentation';

describe('Apresentação de relatórios', () => {
  it('formata datas civis com dia, mês e ano', () => {
    expect(formatReportDate('2026-11-07')).toBe('07/11/2026');
  });

  it('formata eventos com data e hora uniformes', () => {
    expect(formatReportDate('2026-11-07T09:05:00')).toBe('07/11/2026 09:05');
  });

  it('identifica eventos documentais que ainda não ocorreram', () => {
    expect(reportEventDateLabel(null)).toBe('Ainda não ocorreu');
    expect(reportEventDateLabel('2026-11-07T09:05:00')).toBe('07/11/2026 09:05');
  });

  it('preserva a precisão das quantidades e distingue ND de não realizado', () => {
    expect(measurementResultLabel({ kind: 'quantity', value: '0.0001200' })).toBe('0.0001200');
    expect(measurementResultLabel({ kind: 'text', text: 'ND' })).toBe('ND');
    expect(measurementResultLabel({ kind: 'not-detected' })).toBe('Não detectado');
    expect(measurementResultLabel({ kind: 'not-performed' })).toBe('Não realizado');
  });
});
