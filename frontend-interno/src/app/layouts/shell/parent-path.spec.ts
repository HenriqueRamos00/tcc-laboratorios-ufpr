import { backTargetFor } from './parent-path';

describe('Destino Voltar do shell interno', () => {
  it('não mostra Voltar nas raízes e listas de navegação dos perfis', () => {
    expect(backTargetFor('/admin')).toBeNull();
    expect(backTargetFor('/admin/orcamentos')).toBeNull();
    expect(backTargetFor('/tecnico')).toBeNull();
    expect(backTargetFor('/tecnico/ensaios')).toBeNull();
    expect(backTargetFor('/tecnico/relatorios')).toBeNull();
    expect(backTargetFor('/tecnico/resultados')).toBeNull();
  });

  it('retorna de editar, atualizar status e criar ensaio para a lista de ensaios', () => {
    const esperado = { url: '/tecnico/ensaios', label: 'Voltar para Ensaios' };

    expect(backTargetFor('/tecnico/ensaios/novo')).toEqual(esperado);
    expect(backTargetFor('/tecnico/ensaios/804/editar')).toEqual(esperado);
    expect(backTargetFor('/tecnico/ensaios/804/status')).toEqual(esperado);
  });

  it('retorna de detalhes de relatório para a lista canônica', () => {
    expect(backTargetFor('/tecnico/relatorios/804')).toEqual({
      url: '/tecnico/relatorios',
      label: 'Voltar para Relatórios',
    });
    expect(backTargetFor('/tecnico/resultados/804')).toEqual({
      url: '/tecnico/relatorios',
      label: 'Voltar para Relatórios',
    });
  });

  it('ignora consulta e fragmento e não cruza para rotas administrativas', () => {
    expect(backTargetFor('/tecnico/relatorios/804?aba=pdf#topo')).toEqual({
      url: '/tecnico/relatorios',
      label: 'Voltar para Relatórios',
    });
    expect(backTargetFor('/admin/tecnico/ensaios/804/editar')).toBeNull();
    expect(backTargetFor('/login')).toBeNull();
  });
});
