using Portal.Domain.Quotes;

namespace Portal.Application.Quotes;

public interface IClientDecisionRepository
{
    Task AddAsync(ClientDecision decision, CancellationToken cancellationToken);

    Task<IReadOnlyList<ClientDecision>> GetSummariesOfQuotesAsync(
        IReadOnlyCollection<string> quoteIds,
        CancellationToken cancellationToken);
}
