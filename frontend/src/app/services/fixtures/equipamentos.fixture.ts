// Os valores são os exemplos citados nas histórias HU-05 a HU-07, para os
// critérios de aceitação poderem ser conferidos na tela.

import type {
  DetalheDoEquipamento,
  Equipamento,
  RelatorioDoEquipamento,
} from '@/app/model/equipment';

export const EQUIPAMENTOS: readonly Equipamento[] = [
  {
    id: '58836',
    numeroDeSerie: '58836',
    tag: 'TR-B05',
    tipo: 'Transformador',
    localizacao: 'Subestação Norte',
  },
  {
    id: '59210',
    numeroDeSerie: '59210',
    tag: 'TR-B07',
    tipo: 'Transformador',
    localizacao: 'Subestação Sul',
  },
  {
    id: '60101',
    numeroDeSerie: '60101',
    tag: 'TR-C12',
    tipo: 'Transformador',
    localizacao: 'Usina Centro',
  },
  {
    id: '61550',
    numeroDeSerie: '61550',
    tag: 'TR-A01',
    tipo: 'Transformador',
    localizacao: 'Central Leste',
  },
];

const COMPLEMENTO = {
  '58836': {
    tipoDoOleo: 'Mineral Isolante Tipo A',
    empresa: 'ENGIE',
    tensao: '138 kV',
    potencia: '50 MVA',
  },
  '59210': {
    tipoDoOleo: 'Mineral Isolante Tipo A',
    empresa: 'Copel Distribuição',
    tensao: '69 kV',
    potencia: '25 MVA',
  },
  '60101': {
    tipoDoOleo: 'Mineral Isolante Tipo B',
    empresa: 'Petrobras',
    tensao: '138 kV',
    potencia: '40 MVA',
  },
  '61550': {
    tipoDoOleo: 'Vegetal Isolante',
    empresa: 'Sanepar',
    tensao: '34,5 kV',
    potencia: '15 MVA',
  },
} as const satisfies Record<string, Omit<DetalheDoEquipamento, keyof Equipamento>>;

function temComplemento(id: string): id is keyof typeof COMPLEMENTO {
  return id in COMPLEMENTO;
}

export function detalheDoEquipamento(id: string): DetalheDoEquipamento | null {
  const base = EQUIPAMENTOS.find((equipamento) => equipamento.id === id);
  // Sem o guarda, o spread de um complemento ausente montaria um detalhe com
  // quatro campos undefined, que a tela imprime em branco sem erro nenhum.
  if (!base || !temComplemento(id)) return null;
  return { ...base, ...COMPLEMENTO[id] };
}

const TIPOS_DE_ANALISE = [
  'Análise Físico-Química',
  'Cromatografia',
  'Análise de Gases Dissolvidos',
];

// Doze laudos porque o detalhe da tela mostra "1-4 de 12".
export function historicoDoEquipamento(id: string): readonly RelatorioDoEquipamento[] {
  const inicio = new Date('2025-10-26T00:00:00');
  return Array.from({ length: 12 }, (_, indice) => {
    const data = new Date(inicio);
    data.setMonth(data.getMonth() - indice * 3);
    return {
      id: `${id}-${indice + 1}`,
      data: data.toISOString().slice(0, 10),
      tipoDeAnalise: TIPOS_DE_ANALISE[indice % TIPOS_DE_ANALISE.length],
      situacao: 'Concluído',
    };
  });
}
