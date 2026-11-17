using Portal.Application.Quotes.Exceptions;
using Portal.Domain.Quotes;

namespace Portal.Application.Quotes;

public sealed class ClientDecisionRecording : IForRecordingClientDecisions
{
    private readonly IQuoteUseCase _quotes;
    private readonly IClientDecisionRepository _decisions;
    private readonly TimeProvider _time;

    public ClientDecisionRecording(IQuoteUseCase quotes, IClientDecisionRepository decisions, TimeProvider time)
    {
        _quotes = quotes;
        _decisions = decisions;
        _time = time;
    }

    public async Task<ClientDecisionResult> RecordAsync(
        string quoteId,
        ClientDecisionRequest request,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(request);

        QuoteWithLaboratoryStage? found = await _quotes.GetQuoteByIdAsync(quoteId, cancellationToken);

        if (found is null) return new ClientDecisionResult(ClientDecisionOutcome.QuoteNotFound);

        IReadOnlyList<ClientDecision> recorded = await _decisions.GetSummariesOfQuotesAsync(
            [found.Quote.Id], cancellationToken);

        bool allowed = ClientDecisionPolicy.Allows(
            request.Kind,
            CommercialStage.AsTheSourceWroteIt(found.Quote.Stage),
            found.LaboratoryStage,
            recorded);

        if (!allowed) return new ClientDecisionResult(ClientDecisionOutcome.NotAllowed);

        ClientDecision decision = new(
            Guid.NewGuid(),
            found.Quote.Id,
            found.Quote.Code,
            request.Kind,
            request.Author,
            _time.GetUtcNow(),
            request.Terms,
            request.PurchaseOrder,
            request.Justification);

        try
        {
            await _decisions.AddAsync(decision, cancellationToken);
        }
        catch (ClientDecisionConflictException)
        {
            return new ClientDecisionResult(ClientDecisionOutcome.NotAllowed);
        }

        return new ClientDecisionResult(ClientDecisionOutcome.Recorded, decision);
    }
}
