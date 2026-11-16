using Portal.Application.Paging;
using Portal.Application.Pipeline;
using Portal.Domain.Pipeline;
using Portal.Domain.Quotes;

namespace Portal.Application.Quotes;

/// <summary>
/// A aplicacao atras da porta condutora de orcamentos. Ela compoe dois atores conduzidos:
/// a fonte comercial, que sabe o que foi negociado, e a fonte do laboratorio, que sabe o
/// que foi ensaiado. Compor e regra, e por isso mora aqui e nao no controller.
///
/// A listagem pergunta a etapa de todos os orcamentos da pagina de uma vez, entregando as
/// chaves de laboratorio deles (<see cref="LaboratoryQuoteKey"/>), e nao o codigo publico.
/// Perguntar um por um devolveria exatamente a mesma resposta HTTP e faria um adaptador de
/// banco abrir uma consulta por orcamento; perguntar sem os codigos faria ele
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

    public async Task<Page<QuoteWithLaboratoryStage>> GetQuotesAsync(
        PageRequest page,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(page);

        Page<Quote> quotes = await _quoteRepository.GetQuotesAsync(page, cancellationToken);

        if (quotes.Items.Count == 0) return quotes.WithItems<QuoteWithLaboratoryStage>([]);

        // So a pagina servida e perguntada ao laboratorio, e so pelas chaves validas: um
        // orcamento sem codigo de cotacao reconhecivel nao vira pergunta nenhuma.
        string[] keys = [.. quotes.Items
            .Select(quote => quote.LaboratoryKey?.Value)
            .OfType<string>()
            .Distinct(StringComparer.OrdinalIgnoreCase)];

        IReadOnlyList<LaboratoryProgress> progress = keys.Length == 0
            ? []
            : await _laboratoryProgress.GetProgressOfQuotesAsync(keys, cancellationToken);

        return quotes.WithItems<QuoteWithLaboratoryStage>([.. quotes.Items
            .Select(quote => new QuoteWithLaboratoryStage(quote, StageOf(progress, quote.LaboratoryKey)))]);
    }

    public async Task<QuoteWithLaboratoryStage?> GetQuoteByIdAsync(
        string id,
        CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(id)) return null;

        Quote? quote = await _quoteRepository.GetQuoteByIdAsync(id, cancellationToken);

        if (quote is null) return null;
        if (quote.LaboratoryKey is not { } key) return new QuoteWithLaboratoryStage(quote, null);

        LaboratoryProgress? progress = await _laboratoryProgress
            .GetProgressOfQuoteAsync(key.Value, cancellationToken);

        return new QuoteWithLaboratoryStage(quote, progress?.Stage);
    }

    /// <summary>
    /// O download nao passa pela fonte do laboratorio. Ele precisa saber se o orcamento
    /// existe, se o cliente o alcanca e se ja houve proposta escrita, e nada disso depende da
    /// etapa do laboratorio: consultar o AutoLAB aqui seria uma ida a rede por clique de
    /// botao, sem nenhum efeito no arquivo servido. A etapa que decide e a comercial, que
    /// chega dentro do proprio orcamento.
    ///
    /// Orcamento antes da negociacao nao tem documento, e a rota responde o mesmo 404 de
    /// orcamento sem arquivo publicado. A decisao mora aqui, e nao no adaptador, porque vale
    /// para as tres fontes; e a porta conduzida continua sem saber dela, porque o que ela
    /// recebe e o pedido que ja passou pela regra.
    /// </summary>
    public async Task<byte[]?> GetDocumentAsync(string id, CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(id)) return null;

        Quote? quote = await _quoteRepository.GetQuoteByIdAsync(id, cancellationToken);

        if (quote is null) return null;
        if (!ProposalAvailability.ExistsAt(CommercialStage.AsTheSourceWroteIt(quote.Stage))) return null;

        return await _documents.GetDocumentAsync(id, cancellationToken);
    }

    /// <summary>
    /// A etapa que volta do laboratorio e devolvida ao orcamento pela mesma chave com que foi
    /// perguntada, e nunca pelo codigo publico: o <c>OrcamentoCRM</c> que a fonte devolve e a
    /// forma nua da chave, e o <see cref="Quote.Code"/> nao e.
    /// </summary>
    private static PipelineStage? StageOf(IReadOnlyList<LaboratoryProgress> progress, LaboratoryQuoteKey? key)
    {
        if (key is null) return null;

        return progress.FirstOrDefault(item => key.Matches(item.QuoteCode))?.Stage;
    }
}
