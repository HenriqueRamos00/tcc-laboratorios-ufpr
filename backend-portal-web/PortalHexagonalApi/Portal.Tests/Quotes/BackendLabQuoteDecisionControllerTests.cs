using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;

using Portal.Api.Controllers;
using Portal.Api.Quotes;
using Portal.Application.Paging;
using Portal.Application.Quotes;
using Portal.Domain.Quotes;

using Xunit;

namespace Portal.Tests.Quotes;

public sealed class BackendLabQuoteDecisionControllerTests
{
    [Fact]
    public async Task AceiteBackendLabCriaPedidoSemAutor()
    {
        ClientDecisionRequest? submitted = null;
        QuotesController controller = Controller(new Recording(result =>
        {
            submitted = result;
            return new ClientDecisionResult(
                ClientDecisionOutcome.Recorded,
                Receipt: new ClientDecisionReceipt(Guid.NewGuid(), result.Kind, DateTimeOffset.UtcNow));
        }));
        controller.ControllerContext = ContextWithBearer("token-spring");

        IActionResult action = await controller.RecordAcceptance("42", ValidTerms(), CancellationToken.None);

        ObjectResult response = Assert.IsType<ObjectResult>(action);
        Assert.Equal(StatusCodes.Status201Created, response.StatusCode);
        Assert.IsType<ClientDecisionResponse>(response.Value);
        Assert.NotNull(submitted);
        Assert.IsType<BackendLabClientDecisionRequest>(submitted);
        Assert.Equal(ClientDecisionKind.Acceptance, submitted.Kind);
    }

    [Fact]
    public async Task RecusaBackendLabEncaminhaJustificativaSemAutorLactec()
    {
        ClientDecisionRequest? submitted = null;
        QuotesController controller = Controller(new Recording(request =>
        {
            submitted = request;
            return new ClientDecisionResult(
                ClientDecisionOutcome.Recorded,
                Receipt: new ClientDecisionReceipt(Guid.NewGuid(), request.Kind, DateTimeOffset.UtcNow));
        }));
        controller.ControllerContext = ContextWithBearer("token-spring");

        IActionResult action = await controller.RecordRejection("42", new RejectionRequest("Motivo"), CancellationToken.None);

        ObjectResult response = Assert.IsType<ObjectResult>(action);
        Assert.Equal(StatusCodes.Status201Created, response.StatusCode);
        Assert.IsType<ClientDecisionResponse>(response.Value);
        BackendLabClientDecisionRequest request = Assert.IsType<BackendLabClientDecisionRequest>(submitted);
        Assert.Equal(ClientDecisionKind.Rejection, request.Kind);
        Assert.Equal("Motivo", request.Justification);
    }

    [Fact]
    public async Task RecusaBackendLabAceitaJustificativaAusente()
    {
        ClientDecisionRequest? submitted = null;
        QuotesController controller = Controller(new Recording(request =>
        {
            submitted = request;
            return new ClientDecisionResult(
                ClientDecisionOutcome.Recorded,
                Receipt: new ClientDecisionReceipt(Guid.NewGuid(), request.Kind, DateTimeOffset.UtcNow));
        }));

        IActionResult action = await controller.RecordRejection("42", null, CancellationToken.None);

        ObjectResult response = Assert.IsType<ObjectResult>(action);
        Assert.Equal(StatusCodes.Status201Created, response.StatusCode);
        BackendLabClientDecisionRequest request = Assert.IsType<BackendLabClientDecisionRequest>(submitted);
        Assert.Equal(ClientDecisionKind.Rejection, request.Kind);
        Assert.Equal(string.Empty, request.Justification);
    }

    private static QuotesController Controller(IForRecordingClientDecisions decisions) =>
        new(new UnusedQuoteUseCase(), decisions, new Portal.Api.Configuration.PortalOptions
        {
            DataSource = Portal.Api.Configuration.PortalOptions.BackendLabSource
        });

    private static ControllerContext ContextWithBearer(string token)
    {
        DefaultHttpContext context = new();
        context.Request.Headers.Authorization = $"Bearer {token}";
        return new ControllerContext { HttpContext = context };
    }

    private static AcceptanceTermsRequest ValidTerms() => new(
        new BillingAddressRequest(
            "Empresa", "12345678901234", "", "Rua", "Curitiba", "PR", "Centro", "80000000", "Contato",
            "41999999999", ["fiscal@empresa.test", "financeiro@empresa.test"]),
        new AcceptanceApprovalRequest(
            "Solicitante", "Responsável", "Cargo", "Setor", new DateOnly(2026, 11, 20), false, ""));

    private sealed class Recording(Func<ClientDecisionRequest, ClientDecisionResult> record) : IForRecordingClientDecisions
    {
        public Task<ClientDecisionResult> RecordAsync(
            string quoteId, ClientDecisionRequest request, CancellationToken cancellationToken) =>
            Task.FromResult(record(request));
    }

    private sealed class UnusedQuoteUseCase : IQuoteUseCase
    {
        public Task<Page<QuoteWithLaboratoryStage>> GetQuotesAsync(PageRequest page, CancellationToken cancellationToken) =>
            throw new NotSupportedException();

        public Task<QuoteWithLaboratoryStage?> GetQuoteByIdAsync(string id, CancellationToken cancellationToken) =>
            throw new NotSupportedException();

        public Task<byte[]?> GetDocumentAsync(string id, CancellationToken cancellationToken) =>
            throw new NotSupportedException();
    }
}
