import { backTargetFor } from './parent-path';

// O retorno só existe em página aninhada, e "aninhada" aqui é contagem de segmentos: a
// profundidade das rotas do cliente vai de um nível, as três listagens, a quatro, uma análise
// escolhida no histórico de indicadores. Os casos percorrem a fronteira dessa contagem, que é
// onde o controle nasce ou some, e o destino esperado é sempre o último degrau com link da
// trilha que aquela tela desenha.
describe('Degrau acima na área do cliente', () => {
  it('não oferece retorno na raiz de cada seção', () => {
    expect(backTargetFor('/cliente/quotes')).toBeNull();
    expect(backTargetFor('/cliente/relatorios')).toBeNull();
    expect(backTargetFor('/cliente/equipamentos')).toBeNull();
  });

  it('não oferece retorno na raiz da área do cliente', () => {
    expect(backTargetFor('/cliente')).toBeNull();
    expect(backTargetFor('/cliente/')).toBeNull();
  });

  it('não responde por endereço de fora da área do cliente', () => {
    expect(backTargetFor('/login')).toBeNull();
    expect(backTargetFor('/404')).toBeNull();
    expect(backTargetFor('/clientes-externos/alguma/coisa')).toBeNull();
  });

  it('sobe do detalhe do orçamento para a listagem', () => {
    expect(backTargetFor('/cliente/quotes/quote-18')).toEqual({
      url: '/cliente/quotes',
      label: 'Voltar para Orçamentos',
    });
  });

  it('sobe do laudo para a listagem de relatórios', () => {
    expect(backTargetFor('/cliente/relatorios/quote-18')).toEqual({
      url: '/cliente/relatorios',
      label: 'Voltar para Relatórios',
    });
  });

  it('nomeia o registro quando o nível acima termina num identificador', () => {
    expect(backTargetFor('/cliente/quotes/quote-18/aceite')).toEqual({
      url: '/cliente/quotes/quote-18',
      label: 'Voltar para o orçamento',
    });
    expect(backTargetFor('/cliente/equipamentos/NTcxMjg5/indicadores')).toEqual({
      url: '/cliente/equipamentos/NTcxMjg5',
      label: 'Voltar para o equipamento',
    });
  });

  it('sobe da análise escolhida para a visão geral dos indicadores', () => {
    expect(backTargetFor('/cliente/equipamentos/NTcxMjg5/indicadores/TEFDVEVD')).toEqual({
      url: '/cliente/equipamentos/NTcxMjg5/indicadores',
      label: 'Voltar para Indicadores de Saúde',
    });
  });

  it('descarta a consulta e o fragmento, que não são degrau', () => {
    expect(backTargetFor('/cliente/equipamentos/NTcxMjg5/indicadores?aba=gases')).toEqual({
      url: '/cliente/equipamentos/NTcxMjg5',
      label: 'Voltar para o equipamento',
    });
    expect(backTargetFor('/cliente/relatorios?aba=quotes')).toBeNull();
    expect(backTargetFor('/cliente/quotes/quote-18#topo')).toEqual({
      url: '/cliente/quotes',
      label: 'Voltar para Orçamentos',
    });
  });
});
