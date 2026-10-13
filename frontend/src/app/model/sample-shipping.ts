// A amostra sai do cliente e chega ao laboratório sem ninguém acompanhando a
// caixa: o que liga uma coisa à outra é o que está escrito do lado de fora da
// embalagem. Caixa identificada é reconhecida na chegada e segue direto para o
// protocolo, e é isso que o portal pede ao cliente, no mesmo texto, em todo
// lugar onde o envio aparece.

/**
 * Quanto o cliente precisa saber neste ponto do processo. Logo depois do
 * aceite ele ainda não sabe para onde despachar, então vai o endereço junto;
 * com a amostra já a caminho sobra o lembrete da etiqueta.
 */
export type ShippingGuidance = 'labeling' | 'address-and-labeling';

/**
 * Endereço de entrega das amostras, até a API dizer qual unidade recebe o
 * ensaio. É a unidade do Centro Politécnico, e dentro dela o LAC, que é onde
 * correm os processos de laboratório. Não é a sede do Jardim Botânico nem a
 * unidade da Cidade Industrial.
 */
export const LABORATORY_ADDRESS: readonly string[] = [
  'Lactec, LAC, recebimento de amostras',
  'Centro Politécnico da UFPR, Rodovia BR-116, km 98, nº 8813',
  'Jardim das Américas, Curitiba, PR, CEP 81531-980',
];
