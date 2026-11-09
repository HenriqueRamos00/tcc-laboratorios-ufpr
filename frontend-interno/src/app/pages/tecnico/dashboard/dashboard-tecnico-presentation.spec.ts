import {
  ATALHOS_DASHBOARD_TECNICO,
  classeStatusLaudoDashboard,
  INDICADORES_DASHBOARD_TECNICO,
  resumoLaudosPendentes,
} from './dashboard-tecnico-presentation';

describe('Apresentação do dashboard técnico', () => {
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
