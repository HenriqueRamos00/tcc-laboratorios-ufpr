import type { TipoDeAnalise } from './equipment';

// Liga o histórico de relatórios do equipamento à tela de indicadores: o
// histórico escolhe a análise, e esta tabela decide qual aba abre com ela.

/** Aba da tela de indicadores de saúde. Viaja na URL, como `?aba=gases`. */
export type AbaDeIndicadores = 'fisico-quimico' | 'gases';

export const ABAS_DE_INDICADORES: readonly AbaDeIndicadores[] = ['fisico-quimico', 'gases'];

export const ABA_PADRAO: AbaDeIndicadores = 'fisico-quimico';

/**
 * Cromatografia e análise de gases dissolvidos caem na mesma aba porque a tela
 * de gases é uma só. Pergunta aberta para o usuário: os dois tipos devem abrir
 * exatamente a mesma leitura?
 */
export const ABA_DO_TIPO_DE_ANALISE: Record<TipoDeAnalise, AbaDeIndicadores> = {
  'fisico-quimico': 'fisico-quimico',
  cromatografia: 'gases',
  'gases-dissolvidos': 'gases',
};

/** A aba vem da URL, que qualquer pessoa pode digitar errado. */
export function abaConhecida(valor: string | undefined): valor is AbaDeIndicadores {
  return !!valor && (ABAS_DE_INDICADORES as readonly string[]).includes(valor);
}
