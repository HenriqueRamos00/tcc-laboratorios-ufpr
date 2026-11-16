using Portal.Domain.Quotes;

namespace Portal.Application.Quotes;

public enum ClientDecisionOutcome
{
    Recorded,
    QuoteNotFound,
    NotAllowed
}

public sealed record ClientDecisionResult(ClientDecisionOutcome Outcome, ClientDecision? Decision = null);

public sealed record ClientDecisionRequest(
    ClientDecisionKind Kind,
    ClientDecisionAuthor Author,
    AcceptanceTerms? Terms = null,
    PurchaseOrderFile? PurchaseOrder = null,
    string? Justification = null);

public interface IForRecordingClientDecisions
{
    Task<ClientDecisionResult> RecordAsync(
        string quoteId,
        ClientDecisionRequest request,
        CancellationToken cancellationToken);
}
