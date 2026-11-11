import {
  ATALHOS_DASHBOARD_TECNICO,
  classeStatusLaudoDashboard,
  INDICADORES_DASHBOARD_TECNICO,
  resumoLaudosPendentes,
  linhasAtividadeLaudos,
  linhasIdadeLaudos,
} from './dashboard-tecnico-presentation';

describe('Apresentação do dashboard técnico', () => {
  it('preserva os valores da API e os rótulos mensais sem deslocamento de fuso', () => {
    const rows = linhasAtividadeLaudos([{ month: '2026-01', validated: 2, published: 7 }]);
    expect(rows[0].label).toContain('jan.');
    expect(rows[0].label).toContain('2026');
    expect(rows[0].values).toEqual([2, 7]);
    expect(linhasIdadeLaudos({
      upTo7Days: 0, from8To15Days: 3, over15Days: 1, oldestCreatedOn: '2025-12-01',
    }).map((row) => row.values)).toEqual([[0], [3], [1]]);
  });
  it('define somente os três indicadores de ensaio acordados', () => {
    expect(INDICADORES_DASHBOARD_TECNICO.map((card) => card.chave)).toEqual([
      'testsInExecution',
      'testsAwaitingApproval',
      'testsFinished',
    ]);
    expect(INDICADORES_DASHBOARD_TECNICO.map((card) => card.rotulo)).toEqual([
      'Ensaios em execução',
      'Ensaios pendentes de aprovação',
      'Ensaios finalizados',
    ]);
  });

  it('aponta os atalhos para os fluxos existentes do técnico', () => {
    expect(ATALHOS_DASHBOARD_TECNICO.map((atalho) => atalho.rota)).toEqual([
      '/tecnico/ensaios',
      '/tecnico/ensaios/novo',
      '/tecnico/relatorios',
      '/tecnico/equipamentos',
    ]);
  });

  it('informa quando a prévia mostra menos itens que o total pendente', () => {
    expect(resumoLaudosPendentes(8, 6)).toBe(
      'Exibindo 6 de 8 laudos pendentes de validação.',
    );
    expect(resumoLaudosPendentes(1, 1)).toBe('1 laudo pendente de validação.');
    expect(resumoLaudosPendentes(0, 0)).toBe('');
  });

  it('distingue visualmente os estados documentais conhecidos', () => {
    expect(classeStatusLaudoDashboard('pending-validation')).toContain('amber');
    expect(classeStatusLaudoDashboard('validated')).toContain('emerald');
  });
});
