using Portal.Application.Pipeline;
using Portal.Application.Pipeline.Exceptions;
using Portal.Domain.Pipeline;

namespace Portal.Tests.Pipeline;

/// <summary>
/// Duble da porta conduzida de etapas, e o nome diz a tecnologia que ele de fato usa:
/// memoria. Cockburn pede um duble em cada porta justamente para a porta ser diferente de
/// qualquer linha desenhada no diagrama.
///
/// Ele conta quais perguntas recebeu, e nao so o que respondeu. A porta oferece duas
/// perguntas de proposito, e qual delas a aplicacao escolhe e decisao de desenho:
/// perguntar uma vez por orcamento dentro do laco devolve exatamente a mesma resposta HTTP
/// e faz um adaptador de banco abrir uma consulta por linha da listagem. Sem o contador,
/// essa troca passa com a suite verde, e ja passou neste projeto do lado dos ensaios.
/// </summary>
public sealed class InMemoryLaboratoryProgressRepository : ILaboratoryProgressRepository
{
    private readonly List<LaboratoryProgress> _progress;

    public InMemoryLaboratoryProgressRepository(params LaboratoryProgress[] progress)
    {
        _progress = [.. progress];
    }

    public int TimesTheListWasAsked { get; private set; }

    public int TimesOneQuoteWasAsked { get; private set; }

    public List<string> CodesAsked { get; } = [];

    /// <summary>
    /// Quando preenchida, a fonte falha em vez de responder. E o unico jeito de provar o que
    /// a aplicacao faz com a queda do AutoLAB sem derrubar um AutoLAB.
    /// </summary>
    public LaboratoryProgressSourceUnavailableException? Failure { get; init; }

    public Task<IReadOnlyList<LaboratoryProgress>> GetProgressOfQuotesAsync(
        IReadOnlyCollection<string> quoteCodes,
        CancellationToken cancellationToken)
    {
        TimesTheListWasAsked++;
        CodesAsked.AddRange(quoteCodes);

        if (Failure is not null) throw Failure;

        IReadOnlyList<LaboratoryProgress> found = [.. _progress
            .Where(item => quoteCodes.Contains(item.QuoteCode, StringComparer.OrdinalIgnoreCase))];

        return Task.FromResult(found);
    }

    public Task<LaboratoryProgress?> GetProgressOfQuoteAsync(
        string quoteCode,
        CancellationToken cancellationToken)
    {
        TimesOneQuoteWasAsked++;
        CodesAsked.Add(quoteCode);

        if (Failure is not null) throw Failure;

        LaboratoryProgress? found = _progress.FirstOrDefault(item => string.Equals(
            item.QuoteCode, quoteCode, StringComparison.OrdinalIgnoreCase));

        return Task.FromResult(found);
    }
}
