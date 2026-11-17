using Portal.Application.Visibility;
using Portal.Domain.Pipeline;

namespace Portal.Application.Pipeline;

/// <summary>
/// Porta conduzida: o que a aplicacao exige de quem sabe em que pe o laboratorio esta.
///
/// E uma porta separada de <c>ILabTestRepository</c>, embora do outro lado haja hoje o
/// mesmo SQL Server do AutoLAB, e o motivo nao e a tabela: e a conversa. Aqui a pergunta e
/// "em que etapa estao estes orcamentos", e nao "quais sao os ensaios deste equipamento".
///
/// O escopo de empresas esta na assinatura, como nas demais portas do laboratorio. O
/// orcamento ja chega filtrado pelo lado comercial, mas o codigo dele e texto digitado em
/// outro sistema: sem o escopo, um codigo igual ao da solicitacao de outro cliente
/// devolveria a etapa do laboratorio desse outro cliente. Codigo que nao pertence a uma
/// solicitacao das empresas do escopo nao tem etapa.
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
        CompanyScope scope,
        IReadOnlyCollection<string> quoteCodes,
        CancellationToken cancellationToken);

    Task<LaboratoryProgress?> GetProgressOfQuoteAsync(
        CompanyScope scope,
        string quoteCode,
        CancellationToken cancellationToken);
}
