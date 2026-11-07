using Portal.Domain.Pipeline;

namespace Portal.Application.Pipeline;

/// <summary>
/// Porta conduzida: o que a aplicacao exige de quem sabe em que pe o laboratorio esta.
///
/// E uma porta separada de <c>ILabTestRepository</c>, embora do outro lado haja hoje o
/// mesmo SQL Server do AutoLAB, e o motivo nao e a tabela: e a conversa. A porta dos
/// ensaios e escopada por inquilino, porque a fonte dela tem a empresa do transformador e
/// a assinatura nao deixa ninguem esquecer de filtrar. A consulta que informa a etapa tem
/// cinco colunas e nenhuma delas e empresa: nao existe inquilino para pedir, e o escopo de
/// visibilidade do orcamento foi resolvido antes, no lado comercial. Juntar as duas numa
/// porta so obrigaria a receber um inquilino que esta consulta nao sabe filtrar, e uma
/// porta que recebe um filtro que ela ignora promete um isolamento que ela nao entrega.
///
/// O nome diz a intencao, e nao a tecnologia: quem responde pode ser o banco do AutoLAB,
/// uma amostra escrita a mao, um duble da suite, ou ninguem.
///
/// As duas perguntas sao distintas de proposito. A listagem precisa da etapa de varios
/// orcamentos de uma vez, e por isso ela entrega os codigos: um adaptador de banco escreve
/// um <c>WHERE OrcamentoCRM IN (...)</c> em vez de trazer a consulta inteira para descartar
/// quase tudo, que e o defeito que ja aconteceu neste projeto do outro lado. O detalhe
/// pergunta por um codigo so, e nao pode ser atendido filtrando a lista inteira na memoria.
/// </summary>
public interface ILaboratoryProgressRepository
{
    /// <summary>
    /// A etapa dos orcamentos cujos codigos foram pedidos. Orcamento que o laboratorio nao
    /// alcancou nao aparece na resposta, em vez de aparecer com etapa vazia.
    /// </summary>
    Task<IReadOnlyList<LaboratoryProgress>> GetProgressOfQuotesAsync(
        IReadOnlyCollection<string> quoteCodes,
        CancellationToken cancellationToken);

    Task<LaboratoryProgress?> GetProgressOfQuoteAsync(
        string quoteCode,
        CancellationToken cancellationToken);
}
