using Portal.Domain.Quotes;

namespace Portal.Api.Quotes;

public sealed record ClientDecisionResponse(Guid Id, ClientDecisionKind Kind, DateTimeOffset RecordedAt)
{
    public static ClientDecisionResponse? From(ClientDecision? decision) =>
        decision is null ? null : new ClientDecisionResponse(decision.Id, decision.Kind, decision.RecordedAt);
}
