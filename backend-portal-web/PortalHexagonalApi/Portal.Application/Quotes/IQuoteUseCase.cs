using Portal.Domain.Quotes;

namespace Portal.Application.Quotes;

public interface IQuoteUseCase
{
    Task<IReadOnlyList<Quote>> GetQuotesAsync(CancellationToken cancellationToken);
    Task<Quote?> GetQuoteByIdAsync(string id, CancellationToken cancellationToken);
}
