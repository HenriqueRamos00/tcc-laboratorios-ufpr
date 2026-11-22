using Portal.Application.Quotes;
using Portal.Application.Quotes.Exceptions;
using Portal.Domain.Quotes;

using Xunit;

namespace Portal.Tests.Quotes;

public sealed class BackendLabClientDecisionRecordingTests
{
    [Fact]
    public async Task AceiteEncaminhaAoSpringESoRetornaReciboDepoisDaConfirmacao()
    {
        DateTimeOffset recordedAt = new(2026, 11, 20, 14, 30, 0, TimeSpan.FromHours(-3));
        Submission submission = new(ClientDecisionOutcome.Recorded);
        BackendLabClientDecisionRecording recording = new(submission, new FixedTime(recordedAt));

        ClientDecisionResult result = await recording.RecordAsync(
            "42",
            new BackendLabClientDecisionRequest(ClientDecisionKind.Acceptance, new AcceptanceTerms(
                new BillingAddress("Empresa", "12345678901234", "", "Rua", "Curitiba", "PR", "Centro",
                    "80000000", "Contato", "41999999999", ["fiscal@empresa.test", "financeiro@empresa.test"]),
                new AcceptanceApproval("Solicitante", "Responsável", "Cargo", "Setor", new DateOnly(2026, 11, 20),
                    false, ""))),
            CancellationToken.None);

        Assert.Equal(ClientDecisionOutcome.Recorded, result.Outcome);
        Assert.Null(result.Decision);
        Assert.Equal("42", submission.QuoteId);
        Assert.Equal(ClientDecisionKind.Acceptance, submission.Request?.Kind);
        Assert.NotEqual(Guid.Empty, result.Receipt?.Id);
        Assert.Equal(ClientDecisionKind.Acceptance, result.Receipt?.Kind);
        Assert.Equal(recordedAt, result.Receipt?.RecordedAt);
    }

    [Fact]
    public async Task EnvioDeAmostraNaoEDelegadoAoSpringNestaEntrega()
    {
        Submission submission = new(ClientDecisionOutcome.Recorded);
        BackendLabClientDecisionRecording recording = new(submission, TimeProvider.System);

        ClientDecisionResult result = await recording.RecordAsync(
            "42", new BackendLabClientDecisionRequest(ClientDecisionKind.SampleShipment), CancellationToken.None);

        Assert.Equal(ClientDecisionOutcome.NotAllowed, result.Outcome);
        Assert.Null(submission.QuoteId);
    }

    [Fact]
    public async Task AceiteSemTermoRetornaValidacaoAntesDeChamarOAdapter()
    {
        Submission submission = new(ClientDecisionOutcome.Recorded);
        BackendLabClientDecisionRecording recording = new(submission, TimeProvider.System);

        await Assert.ThrowsAsync<ClientDecisionRequestRejectedException>(() => recording.RecordAsync(
            "42", new BackendLabClientDecisionRequest(ClientDecisionKind.Acceptance), CancellationToken.None));

        Assert.Null(submission.QuoteId);
    }

    [Fact]
    public async Task RecusaEncaminhaJustificativaOpcionalAoSpring()
    {
        Submission submission = new(ClientDecisionOutcome.Recorded);
        BackendLabClientDecisionRecording recording = new(submission, TimeProvider.System);

        ClientDecisionResult result = await recording.RecordAsync(
            "42",
            new BackendLabClientDecisionRequest(ClientDecisionKind.Rejection, Justification: "Motivo"),
            CancellationToken.None);

        Assert.Equal(ClientDecisionOutcome.Recorded, result.Outcome);
        Assert.Equal("42", submission.QuoteId);
        Assert.Equal(ClientDecisionKind.Rejection, submission.Request?.Kind);
        Assert.Equal("Motivo", submission.Request?.Justification);
    }

    private sealed class Submission(ClientDecisionOutcome outcome) : IClientDecisionSubmission
    {
        public string? QuoteId { get; private set; }
        public BackendLabClientDecisionRequest? Request { get; private set; }

        public Task<ClientDecisionOutcome> SubmitAsync(
            string quoteId, BackendLabClientDecisionRequest request, CancellationToken cancellationToken)
        {
            QuoteId = quoteId;
            Request = request;
            return Task.FromResult(outcome);
        }
    }

    private sealed class FixedTime(DateTimeOffset now) : TimeProvider
    {
        public override DateTimeOffset GetUtcNow() => now;
    }
}
