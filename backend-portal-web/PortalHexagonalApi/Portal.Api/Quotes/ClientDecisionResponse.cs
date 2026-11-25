using Portal.Application.Quotes;
using Portal.Domain.Quotes;

namespace Portal.Api.Quotes;

public sealed record ClientDecisionResponse(Guid Id, ClientDecisionKind Kind, DateTimeOffset RecordedAt)
{
    public static ClientDecisionResponse? From(ClientDecision? decision) =>
        decision is null ? null : new ClientDecisionResponse(decision.Id, decision.Kind, decision.RecordedAt);

    public static ClientDecisionResponse? From(ClientDecisionResult result)
    {
        if (result.Decision is not null) return From(result.Decision);
        ClientDecisionReceipt? receipt = result.Receipt;
        return receipt is null ? null : new ClientDecisionResponse(receipt.Id, receipt.Kind, receipt.RecordedAt);
    }
}
