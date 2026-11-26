using Portal.Application.Paging;
using Portal.Application.Quotes;
using Portal.Domain.Pipeline;
using Portal.Domain.Quotes;

using Xunit;

namespace Portal.Tests.Quotes;

public sealed class ClientDecisionRecordingTests
{
    private static readonly ClientDecisionAuthor Author = new("42", "Marina Costa", "marina@empresa.com");

    [Fact]
    public async Task OrcamentoForaDoAlcanceDoClienteNaoGravaNada()
    {
        InMemoryClientDecisionRepository store = new();
        ClientDecisionRecording recording = new(new OneQuote(null), store, TimeProvider.System);

        ClientDecisionResult result = await recording.RecordAsync(
            "quote-x", new LactecClientDecisionRequest(ClientDecisionKind.Acceptance, Author), CancellationToken.None);

        Assert.Equal(ClientDecisionOutcome.QuoteNotFound, result.Outcome);
        Assert.Empty(store.Recorded);
    }

    [Fact]
    public async Task GravaComAutorEHorarioEUmSegundoAceiteViraConflito()
    {
        InMemoryClientDecisionRepository store = new();
        DateTimeOffset now = new(2026, 11, 15, 12, 0, 0, TimeSpan.Zero);
        ClientDecisionRecording recording = new(new OneQuote(QuoteAt("Negociação")), store, new FixedTime(now));

        ClientDecisionResult first = await recording.RecordAsync(
            "quote-3", new LactecClientDecisionRequest(ClientDecisionKind.Acceptance, Author), CancellationToken.None);
        ClientDecisionResult second = await recording.RecordAsync(
            "quote-3", new LactecClientDecisionRequest(ClientDecisionKind.PurchaseOrder, Author), CancellationToken.None);

        Assert.Equal(ClientDecisionOutcome.Recorded, first.Outcome);
        ClientDecision saved = Assert.Single(store.Recorded);
        Assert.Equal(("quote-3", "EAQ_2026_45484_V_1", Author, now), (saved.QuoteId, saved.QuoteCode, saved.Author, saved.RecordedAt));
        Assert.Equal(ClientDecisionOutcome.NotAllowed, second.Outcome);
    }

    [Fact]
    public async Task EnvioDaAmostraAntesDoAceiteNaoEPermitido()
    {
        InMemoryClientDecisionRepository store = new();
        ClientDecisionRecording recording = new(new OneQuote(QuoteAt("Negociação")), store, TimeProvider.System);

        ClientDecisionResult result = await recording.RecordAsync(
            "quote-3", new LactecClientDecisionRequest(ClientDecisionKind.SampleShipment, Author), CancellationToken.None);

        Assert.Equal(ClientDecisionOutcome.NotAllowed, result.Outcome);
        Assert.Empty(store.Recorded);
    }

    [Fact]
    public async Task PedidoBackendLabNaoPodeSerGravadoPeloFluxoLactec()
    {
        InMemoryClientDecisionRepository store = new();
        ClientDecisionRecording recording = new(new OneQuote(QuoteAt("Negociação")), store, TimeProvider.System);

        await Assert.ThrowsAsync<ArgumentException>(() => recording.RecordAsync(
            "quote-3", new BackendLabClientDecisionRequest(ClientDecisionKind.Acceptance), CancellationToken.None));

        Assert.Empty(store.Recorded);
    }

    private static QuoteWithLaboratoryStage QuoteAt(string stage) => new(
        new Quote(
            id: "quote-3", opportunityId: "opportunity-3", code: "EAQ_2026_45484_V_1", name: "Proposta",
            status: "Pendente", stage: stage, description: "Teste", companyName: "EMPRESA DE ENERGIA",
            externalContactName: "Comercial", externalContactEmail: "comercial@example.com",
            totalPrice: 100m, createdDate: null),
        null);

    private sealed class OneQuote(QuoteWithLaboratoryStage? quote) : IQuoteUseCase
    {
        public Task<Page<QuoteWithLaboratoryStage>> GetQuotesAsync(PageRequest page, CancellationToken cancellationToken) =>
            throw new NotSupportedException();

        public Task<QuoteWithLaboratoryStage?> GetQuoteByIdAsync(string id, CancellationToken cancellationToken) =>
            Task.FromResult(quote?.Quote.Id == id ? quote : null);

        public Task<byte[]?> GetDocumentAsync(string id, CancellationToken cancellationToken) =>
            throw new NotSupportedException();
    }

    private sealed class FixedTime(DateTimeOffset now) : TimeProvider
    {
        public override DateTimeOffset GetUtcNow() => now;
    }
}
