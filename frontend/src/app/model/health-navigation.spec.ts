import { ANALYSIS_TYPE_LABEL, type ReportAnalysisType } from './equipment';

import { isKnownTab, TAB_BY_ANALYSIS_TYPE } from './health-navigation';

describe('navegação dos indicadores de saúde', () => {
  it('abre uma aba conhecida para todo tipo de análise com indicador', () => {
    const tipos = (Object.keys(ANALYSIS_TYPE_LABEL) as ReportAnalysisType[]).filter((tipo) => tipo !== 'other');

    expect(tipos.length).toBeGreaterThan(0);
    tipos.forEach((tipo) => expect(isKnownTab(TAB_BY_ANALYSIS_TYPE[tipo] ?? undefined)).toBeTrue());
  });

  it('não abre aba para laudo de outro ensaio, que só tem o documento', () => {
    expect(TAB_BY_ANALYSIS_TYPE.other).toBeNull();
  });

  it('manda cromatografia para a aba de gases e físico-química para a sua', () => {
    expect(TAB_BY_ANALYSIS_TYPE.chromatography).toBe('gases');
    expect(TAB_BY_ANALYSIS_TYPE['physical-chemical']).toBe('fisico-quimico');
  });

  it('recusa aba que não existe, para a URL digitada à mão cair no padrão', () => {
    expect(isKnownTab('gases')).toBeTrue();
    expect(isKnownTab('gazes')).toBeFalse();
    expect(isKnownTab(undefined)).toBeFalse();
  });
});
