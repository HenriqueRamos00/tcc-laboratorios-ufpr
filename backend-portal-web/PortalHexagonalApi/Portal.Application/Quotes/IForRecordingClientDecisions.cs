using Portal.Domain.Quotes;

namespace Portal.Application.Quotes;

public enum ClientDecisionOutcome
{
    Recorded,
    QuoteNotFound,
    NotAllowed
}

public sealed record ClientDecisionReceipt(Guid Id, ClientDecisionKind Kind, DateTimeOffset RecordedAt);

public sealed record ClientDecisionResult(
    ClientDecisionOutcome Outcome,
    ClientDecision? Decision = null,
    ClientDecisionReceipt? Receipt = null);

public abstract record ClientDecisionRequest(
    ClientDecisionKind Kind,
    AcceptanceTerms? Terms = null,
    PurchaseOrderFile? PurchaseOrder = null,
    string? Justification = null);

public sealed record LactecClientDecisionRequest(
    ClientDecisionKind Kind,
    ClientDecisionAuthor Author,
    AcceptanceTerms? Terms = null,
    PurchaseOrderFile? PurchaseOrder = null,
    string? Justification = null)
    : ClientDecisionRequest(Kind, Terms, PurchaseOrder, Justification);

public sealed record BackendLabClientDecisionRequest(
    ClientDecisionKind Kind,
    AcceptanceTerms? Terms = null,
    PurchaseOrderFile? PurchaseOrder = null,
    string? Justification = null)
    : ClientDecisionRequest(Kind, Terms, PurchaseOrder, Justification);

public interface IClientDecisionSubmission
{
    Task<ClientDecisionOutcome> SubmitAsync(
        string quoteId,
        BackendLabClientDecisionRequest request,
        CancellationToken cancellationToken);
}

public interface IForRecordingClientDecisions
{
    Task<ClientDecisionResult> RecordAsync(
        string quoteId,
        ClientDecisionRequest request,
        CancellationToken cancellationToken);
}
