export interface BackTarget {
  readonly url: string;
  readonly label: string;
}

const TECHNICIAN_COLLECTIONS: Readonly<Record<string, BackTarget>> = {
  ensaios: { url: '/tecnico/ensaios', label: 'Voltar para Ensaios' },
  relatorios: { url: '/tecnico/relatorios', label: 'Voltar para Relatórios' },
  resultados: { url: '/tecnico/relatorios', label: 'Voltar para Relatórios' },
};

/** Devolve a lista interna correta para detalhes e formulários acessados por link direto. */
export function backTargetFor(url: string): BackTarget | null {
  const path = url.split('?')[0].split('#')[0];
  const segments = path.split('/').filter(Boolean);

  if (segments[0] !== 'tecnico') return null;

  const collection = TECHNICIAN_COLLECTIONS[segments[1] ?? ''];
  if (!collection) return null;

  const action = segments[2];
  const isNewTest = collection.url === '/tecnico/ensaios' && action === 'novo';
  const isTestDetail = collection.url === '/tecnico/ensaios' && segments.length >= 4;
  const isReportDetail =
    (segments[1] === 'relatorios' || segments[1] === 'resultados') && segments.length === 3;

  return isNewTest || isTestDetail || isReportDetail ? collection : null;
}
