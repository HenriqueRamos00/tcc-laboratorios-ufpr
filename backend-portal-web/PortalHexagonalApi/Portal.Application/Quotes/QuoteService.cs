using Portal.Domain.Quotes;

namespace Portal.Application.Quotes;

public sealed class QuoteService : IQuoteUseCase
{
    private readonly IQuoteRepository _quoteRepository;

    public QuoteService(IQuoteRepository quoteRepository)
    {
        _quoteRepository = quoteRepository;
    }

    public Task<IReadOnlyList<Quote>> GetQuotesAsync(CancellationToken cancellationToken)
    {
        return _quoteRepository.GetQuotesAsync(cancellationToken);
    }

    public Task<Quote?> GetQuoteByIdAsync(string id, CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(id))
            return Task.FromResult<Quote?>(null);

        return _quoteRepository.GetQuoteByIdAsync(id, cancellationToken);
    }
}
