const CLIENT_AREA = '/cliente';

/** O destino do retorno: para onde o link leva e como o leitor de tela o anuncia. */
export interface BackTarget {
  readonly url: string;
  readonly label: string;
}

/** O nome da seção quando ela própria é o destino do retorno. */
const SECTION_NAMES: Readonly<Record<string, string>> = {
  quotes: 'Orçamentos',
  relatorios: 'Relatórios',
  equipamentos: 'Equipamentos',
  indicadores: 'Indicadores de Saúde',
};

/** O nome do registro quando o destino é um detalhe, cujo último segmento é identificador. */
const ITEM_NAMES: Readonly<Record<string, string>> = {
  quotes: 'o orçamento',
  relatorios: 'o relatório',
  equipamentos: 'o equipamento',
};

/**
 * O degrau acima da página na hierarquia de endereços, ou nulo quando a página já é raiz de
 * seção e não existe para onde subir dentro do portal.
 *
 * O retorno sobe na hierarquia e não desfaz o histórico do navegador, e a diferença importa
 * porque este portal é consumido por link direto: o cliente recebe o aviso de laudo publicado
 * e abre o endereço numa aba nova. Ali `history.back()` ou não faz nada, porque não existe
 * página anterior naquela aba, ou devolve o cliente para fora do portal. Subir um nível
 * responde sempre a mesma coisa, tenha ele chegado navegando ou pelo link do e-mail.
 *
 * Consulta e fragmento caem fora antes da contagem: a aba selecionada na tela de indicadores
 * viaja em `?aba=`, e ela é estado daquela tela, não um degrau da hierarquia.
 */
export function backTargetFor(url: string): BackTarget | null {
  const path = url.split('?')[0].split('#')[0];
  if (path !== CLIENT_AREA && !path.startsWith(`${CLIENT_AREA}/`)) return null;

  const segments = path.slice(CLIENT_AREA.length).split('/').filter(Boolean);
  if (segments.length < 2) return null;

  const parent = segments.slice(0, -1);
  return { url: [CLIENT_AREA, ...parent].join('/'), label: labelFor(parent) };
}

/**
 * O destino nomeado para quem usa leitor de tela. Quando o nível acima termina num
 * identificador opaco, que não serve de rótulo, o nome vem da seção a que ele pertence.
 */
function labelFor(parent: readonly string[]): string {
  const last = parent[parent.length - 1] ?? '';
  const section = SECTION_NAMES[last];
  if (section) return `Voltar para ${section}`;

  const owner = parent[parent.length - 2] ?? '';
  const item = ITEM_NAMES[owner];
  return item ? `Voltar para ${item}` : 'Voltar';
}
