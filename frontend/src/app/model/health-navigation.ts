import type { ReportAnalysisType } from './equipment';

// Liga o histórico de relatórios do equipamento à tela de indicadores: o
// histórico escolhe a análise, e esta tabela decide qual aba abre com ela.

/** Aba da tela de indicadores de saúde. Viaja na URL, como `?aba=gases`. */
export type HealthTab = 'fisico-quimico' | 'gases';

export const HEALTH_TABS: readonly HealthTab[] = ['fisico-quimico', 'gases'];

export const DEFAULT_HEALTH_TAB: HealthTab = 'fisico-quimico';

/**
 * Laudo de cromatografia abre a aba de gases, que é a que tem gráfico, totalizador e
 * diagnóstico. Laudo físico-químico abre a sua.
 */
export const TAB_BY_ANALYSIS_TYPE: Record<ReportAnalysisType, HealthTab> = {
  'physical-chemical': 'fisico-quimico',
  chromatography: 'gases',
};

/** A aba vem da URL, que qualquer pessoa pode digitar errado. */
export function isKnownTab(value: string | undefined): value is HealthTab {
  return !!value && (HEALTH_TABS as readonly string[]).includes(value);
}
