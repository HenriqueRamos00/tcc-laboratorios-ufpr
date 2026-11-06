using Portal.Application.Pipeline;
using Portal.Domain.Pipeline;
using Portal.Domain.Quotes;

namespace Portal.Application.Quotes;

/// <summary>
/// A aplicacao atras da porta condutora de orcamentos. Ela compoe dois atores conduzidos:
/// a fonte comercial, que sabe o que foi negociado, e a fonte do laboratorio, que sabe o
/// que foi ensaiado. Compor e regra, e por isso mora aqui e nao no controller.
///
/// A listagem pergunta a etapa de todos os orcamentos de uma vez, entregando os codigos
/// deles. Perguntar um por um devolveria exatamente a mesma resposta HTTP e faria um
/// adaptador de banco abrir uma consulta por orcamento; perguntar sem os codigos faria ele
/// trazer a consulta inteira para descartar quase tudo. Nenhum teste de contrato enxerga
/// essa troca, e e por isso que o duble da porta conta quais perguntas recebeu.
/// </summary>
public sealed class QuoteService : IQuoteUseCase
{
    private readonly IQuoteRepository _quoteRepository;
    private readonly IQuoteDocumentRepository _documents;
    private readonly ILaboratoryProgressRepository _laboratoryProgress;

    public QuoteService(
        IQuoteRepository quoteRepository,
        IQuoteDocumentRepository documents,
        ILaboratoryProgressRepository laboratoryProgress)
    {
        _quoteRepository = quoteRepository;
        _documents = documents;
        _laboratoryProgress = laboratoryProgress;
    }

    public async Task<IReadOnlyList<QuoteWithLaboratoryStage>> GetQuotesAsync(
        CancellationToken cancellationToken)
    {
        IReadOnlyList<Quote> quotes = await _quoteRepository.GetQuotesAsync(cancellationToken);

        if (quotes.Count == 0) return [];

        string[] codes = [.. quotes
            .Select(quote => quote.Code)
            .Where(code => !string.IsNullOrWhiteSpace(code))
            .Distinct(StringComparer.OrdinalIgnoreCase)];

        IReadOnlyList<LaboratoryProgress> progress = codes.Length == 0
            ? []
            : await _laboratoryProgress.GetProgressOfQuotesAsync(codes, cancellationToken);

        return [.. quotes.Select(quote => new QuoteWithLaboratoryStage(quote, StageOf(progress, quote.Code)))];
    }

    public async Task<QuoteWithLaboratoryStage?> GetQuoteByIdAsync(
        string id,
        CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(id)) return null;

        Quote? quote = await _quoteRepository.GetQuoteByIdAsync(id, cancellationToken);

        if (quote is null) return null;
        if (string.IsNullOrWhiteSpace(quote.Code)) return new QuoteWithLaboratoryStage(quote, null);

        LaboratoryProgress? progress = await _laboratoryProgress
            .GetProgressOfQuoteAsync(quote.Code, cancellationToken);

        return new QuoteWithLaboratoryStage(quote, progress?.Stage);
    }

    /// <summary>
    /// O download nao passa pela fonte do laboratorio. Ele precisa saber se o orcamento
    /// existe e se o cliente o alcanca, e nada disso depende da etapa: consultar o AutoLAB
    /// aqui seria uma ida a rede por clique de botao, sem nenhum efeito no arquivo servido.
    /// </summary>
    public async Task<byte[]?> GetDocumentAsync(string id, CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(id)) return null;

        if (await _quoteRepository.GetQuoteByIdAsync(id, cancellationToken) is null) return null;

        return await _documents.GetDocumentAsync(id, cancellationToken);
    }

    private static PipelineStage? StageOf(IReadOnlyList<LaboratoryProgress> progress, string quoteCode)
    {
        if (string.IsNullOrWhiteSpace(quoteCode)) return null;

        return progress
            .FirstOrDefault(item => string.Equals(
                item.QuoteCode,
                quoteCode.Trim(),
                StringComparison.OrdinalIgnoreCase))
            ?.Stage;
    }
}
