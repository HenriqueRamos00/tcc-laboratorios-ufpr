using Portal.Application.Quotes;
using Portal.Application.Quotes.Exceptions;
using Portal.Domain.Quotes;

namespace Portal.Adapters.PortalStore;

public sealed class NotConnectedClientDecisionRepository : IClientDecisionRepository
{
    public Task AddAsync(ClientDecision decision, CancellationToken cancellationToken) =>
        throw new ClientDecisionStoreUnavailableException("O banco do portal nao esta configurado.", connected: false);

    public Task<IReadOnlyList<ClientDecision>> GetSummariesOfQuotesAsync(
        IReadOnlyCollection<string> quoteIds,
        CancellationToken cancellationToken) =>
        Task.FromResult<IReadOnlyList<ClientDecision>>([]);
}
