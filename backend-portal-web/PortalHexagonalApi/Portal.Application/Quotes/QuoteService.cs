using Portal.Domain.Quotes;

namespace Portal.Application.Quotes;

public sealed class QuoteService : IQuoteUseCase
{
    private readonly IQuoteRepository _quoteRepository;
    private readonly IQuoteDocumentRepository _documents;

    public QuoteService(IQuoteRepository quoteRepository, IQuoteDocumentRepository documents)
    {
        _quoteRepository = quoteRepository;
        _documents = documents;
    }

    public Task<IReadOnlyList<Quote>> GetQuotesAsync(CancellationToken cancellationToken)
    {
        return _quoteRepository.GetQuotesAsync(cancellationToken);
    }

    public async Task<byte[]?> GetDocumentAsync(string id, CancellationToken cancellationToken)
    {
        if (await GetQuoteByIdAsync(id, cancellationToken) is null) return null;
        return await _documents.GetDocumentAsync(id, cancellationToken);
    }

    public Task<Quote?> GetQuoteByIdAsync(string id, CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(id))
            return Task.FromResult<Quote?>(null);

        return _quoteRepository.GetQuoteByIdAsync(id, cancellationToken);
    }
}
