using Portal.Application.Quotes;
using Portal.Application.Quotes.Exceptions;
using Portal.Domain.Quotes;

namespace Portal.Tests.Quotes;

internal sealed class InMemoryClientDecisionRepository : IClientDecisionRepository
{
    private readonly List<ClientDecision> _decisions = [];

    public IReadOnlyList<ClientDecision> Recorded => _decisions;

    public Task AddAsync(ClientDecision decision, CancellationToken cancellationToken)
    {
        bool shipment = decision.Kind == ClientDecisionKind.SampleShipment;

        if (_decisions.Any(existing => existing.QuoteId == decision.QuoteId
                && (existing.Kind == ClientDecisionKind.SampleShipment) == shipment))
            throw new ClientDecisionConflictException("Vaga ocupada.", new InvalidOperationException());

        _decisions.Add(decision);
        return Task.CompletedTask;
    }

    public Task<IReadOnlyList<ClientDecision>> GetSummariesOfQuotesAsync(
        IReadOnlyCollection<string> quoteIds,
        CancellationToken cancellationToken) =>
        Task.FromResult<IReadOnlyList<ClientDecision>>(
            [.. _decisions.Where(decision => quoteIds.Contains(decision.QuoteId))]);
}
