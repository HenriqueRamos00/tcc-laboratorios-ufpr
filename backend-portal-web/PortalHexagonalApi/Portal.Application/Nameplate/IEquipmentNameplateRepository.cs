using Portal.Domain.Nameplate;

namespace Portal.Application.Nameplate;

/// <summary>
/// Porta conduzida: o que a aplicacao exige de quem guarda o cadastro de equipamentos do
/// AutoLAB.
///
/// Juan aconselha, de experiencia propria, fazer porta conduzida para sistema externo e nao
/// para conceito de dominio, e e esse teste que esta porta passa. O ator conduzido aqui e a
/// tabela <c>Equipamentos</c> do SQL Server do AutoLAB, e o livro define sistema externo como
/// aquele cuja interface este time nao pode mudar: esta tabela e do laboratorio, tem dez
/// colunas que ninguem daqui escolheu e nao vai ganhar um <c>id</c> porque o portal precisa.
/// A porta nao e "um jeito de construir instancias de equipamento": nao existe porta para
/// <c>TestedEquipment</c>, que continua nascendo do agrupamento das analises dentro do
/// hexagono, e o erro que Juan descreve e exatamente esse, por uma porta no conceito em vez
/// de no sistema.
///
/// E porta separada de <c>ILabTestRepository</c>, embora do outro lado haja hoje o mesmo SQL
/// Server, e o motivo nao e a tabela: e a conversa. Cockburn recomenda comecar com uma porta
/// por ator secundario, porque e isso que casa com as intencoes das conversas. A porta dos
/// ensaios tem o inquilino em toda assinatura, porque a fonte dela tem a empresa do
/// transformador e a assinatura nao deixa ninguem esquecer de filtrar. O cadastro nao tem
/// coluna de empresa nenhuma: nao existe inquilino para pedir. Juntar as duas numa porta so
/// obrigaria esta consulta a receber um inquilino que ela nao sabe filtrar, e uma porta que
/// recebe um filtro que ela ignora promete um isolamento que ela nao entrega.
///
/// Como entao o cadastro nao vira caminho para um cliente ver equipamento de outro: por
/// construcao, e nao por filtro. Esta porta nao tem operacao que liste, nao tem operacao que
/// busque por TAG e nao tem operacao que aceite parte de uma serie. A unica forma de entrar e
/// com numeros de serie que quem pergunta ja tem na mao, e a aplicacao so tem na mao os
/// numeros de serie que <c>ILabTestRepository</c> devolveu para aquele inquilino. A chave que
/// chega na rota nunca alcanca esta porta: ela e conferida contra as analises do inquilino
/// primeiro, e equipamento que nao e do inquilino responde 404 antes de o cadastro ser
/// consultado.
///
/// O nome diz a intencao, e nao a tecnologia: quem responde pode ser o banco do AutoLAB, uma
/// amostra escrita a mao, um duble da suite, ou ninguem.
///
/// As duas perguntas sao distintas de proposito, pelo mesmo motivo da porta de etapas. A
/// listagem precisa da placa de varios equipamentos de uma vez e por isso entrega as series:
/// um adaptador de banco escreve um <c>WHERE NumSerie IN (...)</c> em vez de trazer as 20575
/// linhas para descartar quase todas, ou de abrir uma consulta por linha da tela. O detalhe
/// pergunta por uma serie so, e nao pode ser atendido filtrando a lista inteira na memoria.
/// </summary>
public interface IEquipmentNameplateRepository
{
    /// <summary>
    /// A placa das series pedidas. Serie que o cadastro nao conhece nao aparece na resposta,
    /// em vez de aparecer com todos os campos vazios, e serie cuja ambiguidade nao deixa
    /// nenhum campo de pe tambem nao aparece. Quem pergunta casa a resposta pela serie, entao
    /// a ordem da lista nao e contrato.
    /// </summary>
    Task<IReadOnlyList<EquipmentNameplate>> GetNameplatesOfAsync(
        IReadOnlyCollection<string> serialNumbers,
        CancellationToken cancellationToken);

    Task<EquipmentNameplate?> GetNameplateOfAsync(
        string serialNumber,
        CancellationToken cancellationToken);
}
