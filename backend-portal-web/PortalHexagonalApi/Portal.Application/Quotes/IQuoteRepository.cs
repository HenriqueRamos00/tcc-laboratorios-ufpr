using Portal.Domain.Quotes;

namespace Portal.Application.Quotes;

public interface IQuoteRepository
{
    // Quote includes the Opportunity data required by the portal.
    Task<IReadOnlyList<Quote>> GetQuotesAsync(CancellationToken cancellationToken);
    Task<Quote?> GetQuoteByIdAsync(string id, CancellationToken cancellationToken);
}
