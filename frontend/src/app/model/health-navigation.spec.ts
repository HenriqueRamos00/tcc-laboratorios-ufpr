import {
  analiseSelecionadaDoHistorico,
  historicoDoEquipamento,
} from '@/app/services/fixtures/equipamentos.fixture';

import { ABA_DO_TIPO_DE_ANALISE, abaConhecida } from './health-navigation';

describe('navegação dos indicadores de saúde', () => {
  it('abre a aba certa para cada tipo de análise do histórico', () => {
    const abas = historicoDoEquipamento('60101').map(
      (relatorio) => ABA_DO_TIPO_DE_ANALISE[relatorio.tipoDeAnalise],
    );

    expect(new Set(abas)).toEqual(new Set(['fisico-quimico', 'gases']));
  });

  it('recusa aba que não existe, para a URL digitada à mão cair no padrão', () => {
    expect(abaConhecida('gases')).toBeTrue();
    expect(abaConhecida('gazes')).toBeFalse();
    expect(abaConhecida(undefined)).toBeFalse();
  });

  it('nomeia a análise escolhida a partir do identificador que veio na URL', () => {
    const primeira = historicoDoEquipamento('60101')[0];

    expect(analiseSelecionadaDoHistorico('60101', primeira.id)).toEqual({
      id: primeira.id,
      data: primeira.data,
      rotulo: 'Análise Físico-Química',
    });
  });

  it('não inventa análise para a visão geral nem para identificador desconhecido', () => {
    expect(analiseSelecionadaDoHistorico('60101', undefined)).toBeNull();
    expect(analiseSelecionadaDoHistorico('60101', '60101-999')).toBeNull();
  });
});
