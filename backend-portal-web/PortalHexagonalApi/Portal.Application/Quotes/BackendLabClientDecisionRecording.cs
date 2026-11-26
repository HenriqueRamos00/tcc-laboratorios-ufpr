using Portal.Application.Quotes.Exceptions;
using Portal.Domain.Quotes;

namespace Portal.Application.Quotes;

public sealed class BackendLabClientDecisionRecording(
    IClientDecisionSubmission submission,
    TimeProvider time) : IForRecordingClientDecisions
{
    public async Task<ClientDecisionResult> RecordAsync(
        string quoteId,
        ClientDecisionRequest request,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(request);

        if (request is not BackendLabClientDecisionRequest backendLabRequest)
            return new ClientDecisionResult(ClientDecisionOutcome.NotAllowed);

        if (request.Kind is not (
            ClientDecisionKind.Acceptance or ClientDecisionKind.PurchaseOrder or ClientDecisionKind.Rejection))
            return new ClientDecisionResult(ClientDecisionOutcome.NotAllowed);

        if (request.Kind == ClientDecisionKind.Acceptance && request.Terms is null)
            throw new ClientDecisionRequestRejectedException("O termo de aceite está incompleto.");

        if (request.Kind == ClientDecisionKind.PurchaseOrder && request.PurchaseOrder is null)
            throw new ClientDecisionRequestRejectedException("Envie a ordem de compra.");

        ClientDecisionOutcome outcome = await submission.SubmitAsync(quoteId, backendLabRequest, cancellationToken);
        if (outcome != ClientDecisionOutcome.Recorded)
            return new ClientDecisionResult(outcome);

        // O contrato público retorna um recibo da chamada. O identificador da decisão e
        // a data persistidos continuam sob responsabilidade do Spring.
        ClientDecisionReceipt receipt = new(Guid.NewGuid(), request.Kind, time.GetUtcNow());
        return new ClientDecisionResult(ClientDecisionOutcome.Recorded, Receipt: receipt);
    }
}
