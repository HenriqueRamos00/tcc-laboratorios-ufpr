import {
  apresentarOrcamentoTecnico,
  apresentarStatusTecnico,
  formatarDataCriacaoTecnica,
  OPCOES_STATUS_TECNICO,
} from './orcamento-tecnico-presentation';

describe('Apresentação de orçamentos técnicos', () => {
  it('apresenta os rótulos dos estados técnicos previstos', () => {
    expect(apresentarStatusTecnico('incomplete').label).toBe('Incompleto');
    expect(apresentarStatusTecnico('complete').label).toBe('Completo');
    expect(apresentarStatusTecnico('validated').label).toBe('Validado');
    expect(apresentarStatusTecnico('report-published').label).toBe('Publicado');
    expect(apresentarStatusTecnico('cancelled').label).toBe('Cancelado');
  });

  it('apresenta o resumo cancelado com estilo neutro', () => {
    const status = apresentarStatusTecnico('cancelled');

    expect(status.label).toBe('Cancelado');
    expect(status.className).toContain('bg-lactec-line-soft');
  });

  it('inclui Cancelado entre as opções de filtro técnico', () => {
    expect(OPCOES_STATUS_TECNICO).toContain({ value: 'cancelled', label: 'Cancelado' });
  });

  it('apresenta status ausente como não informado em estilo neutro', () => {
    const status = apresentarStatusTecnico(null);

    expect(status.label).toBe('Não informado');
    expect(status.className).toContain('bg-lactec-line-soft');
    expect(status.label).not.toBe('Incompleto');
  });

  it('formata a data no fuso de São Paulo sem deslocar o dia', () => {
    expect(formatarDataCriacaoTecnica('2026-10-18T02:30:00Z')).toBe('17/10/2026');
    expect(formatarDataCriacaoTecnica(null)).toBe('-');
    expect(formatarDataCriacaoTecnica('data inválida')).toBe('-');
  });

  it('mantém os campos do contrato ao criar a apresentação da linha', () => {
    const linha = apresentarOrcamentoTecnico({
      id: 'quote-1',
      code: 'EAQ_2026_33322_V_3',
      name: 'Análise de óleo isolante',
      companyName: 'Copel Distribuição',
      createdDate: '2026-10-15T10:00:00-03:00',
      technicalStatus: 'validated',
    });

    expect(linha.code).toBe('EAQ_2026_33322_V_3');
    expect(linha.technicalStatusLabel).toBe('Validado');
    expect(linha.createdDateLabel).toBe('15/10/2026');
  });
});
