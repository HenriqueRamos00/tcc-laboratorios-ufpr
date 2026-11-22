using Microsoft.AspNetCore.Mvc;

using Portal.Api.Authentication;
using Portal.Api.Configuration;
using Portal.Api.LabResults;
using Portal.Api.Quotes;
using Portal.Application.Paging;
using Portal.Application.Pipeline.Exceptions;
using Portal.Application.Quotes;
using Portal.Application.Quotes.Exceptions;
using Portal.Domain.Quotes;

namespace Portal.Api.Controllers;

/// <summary>
/// Os tres caminhos de orcamento. O controller resolve o endereco, chama a porta condutora
/// e traduz; nenhuma regra de orcamento passa por aqui, nem a composicao da etapa do
/// laboratorio com a etapa comercial, que e regra e mora na aplicacao.
///
/// As cinco falhas possiveis eram tratadas em cada metodo, com o mesmo bloco repetido tres
/// vezes e um corpo de erro proprio, diferente do que o resto da API publica. Agora o
/// tratamento fica em <see cref="AnswerAsync"/> e o corpo sai de
/// <see cref="ProblemResponses"/>, que e o mesmo das demais rotas.
/// </summary>
[ApiController]
[NoStore]
[Route("api/quotes")]
public sealed class QuotesController : ControllerBase
{
    public const int MaxPurchaseOrderBytes = 50 * 1024 * 1024;

    private static readonly (string Extension, byte[] Signature)[] PurchaseOrderFormats =
    [
        (".pdf", "%PDF-"u8.ToArray()),
        (".doc", [0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1]),
        (".docx", [(byte)'P', (byte)'K', 0x03, 0x04])
    ];

    private readonly IQuoteUseCase _quoteUseCase;
    private readonly IForRecordingClientDecisions _decisions;
    private readonly PortalOptions _portal;

    public QuotesController(IQuoteUseCase quoteUseCase, IForRecordingClientDecisions decisions, PortalOptions portal)
    {
        _quoteUseCase = quoteUseCase;
        _decisions = decisions;
        _portal = portal;
    }

    /// <summary>
    /// A lista paginada, na mesma convencao das demais listas da API: <c>page</c> e
    /// <c>pageSize</c> na query, o array da pagina no corpo e o total nos cabecalhos
    /// <c>X-Total-Count</c>, <c>X-Page</c> e <c>X-Page-Size</c>.
    /// </summary>
    [HttpGet]
    [ProducesResponseType(typeof(IReadOnlyList<QuoteResponse>), StatusCodes.Status200OK)]
    [ProducesResponseType<ProblemDetails>(StatusCodes.Status400BadRequest)]
    [ProducesResponseType<ProblemDetails>(StatusCodes.Status401Unauthorized)]
    [ProducesResponseType<ProblemDetails>(StatusCodes.Status403Forbidden)]
    [ProducesResponseType(StatusCodes.Status502BadGateway)]
    public Task<IActionResult> GetQuotes([FromQuery] PageQuery paging, CancellationToken cancellationToken)
    {
        if (!paging.TryToRequest(out PageRequest request))
            return Task.FromResult(this.InvalidPage());

        return AnswerAsync(async () =>
        {
            Page<QuoteWithLaboratoryStage> quotes =
                await _quoteUseCase.GetQuotesAsync(request, cancellationToken);

            PageQuery.WriteHeaders(Response, quotes);
            return Ok(quotes.Items.Select(QuoteResponse.From));
        });
    }

    [HttpGet("{id}")]
    [ProducesResponseType(typeof(QuoteResponse), StatusCodes.Status200OK)]
    [ProducesResponseType<ProblemDetails>(StatusCodes.Status401Unauthorized)]
    [ProducesResponseType<ProblemDetails>(StatusCodes.Status403Forbidden)]
    [ProducesResponseType(StatusCodes.Status404NotFound)]
    [ProducesResponseType<ProblemDetails>(StatusCodes.Status502BadGateway)]
    public Task<IActionResult> GetQuoteById(string id, CancellationToken cancellationToken)
    {
        return AnswerAsync(async () =>
        {
            QuoteWithLaboratoryStage? quote = await _quoteUseCase.GetQuoteByIdAsync(id, cancellationToken);

            return quote is null ? this.QuoteNotFound() : Ok(QuoteResponse.From(quote));
        });
    }

    [HttpGet("{id}/document")]
    [ProducesResponseType(typeof(FileContentResult), StatusCodes.Status200OK, "application/pdf")]
    [ProducesResponseType<ProblemDetails>(StatusCodes.Status401Unauthorized)]
    [ProducesResponseType<ProblemDetails>(StatusCodes.Status403Forbidden)]
    [ProducesResponseType(StatusCodes.Status404NotFound)]
    [ProducesResponseType(StatusCodes.Status502BadGateway)]
    public Task<IActionResult> GetDocument(string id, CancellationToken cancellationToken)
    {
        return AnswerAsync(async () =>
        {
            byte[]? document = await _quoteUseCase.GetDocumentAsync(id, cancellationToken);

            return document is null
                ? this.QuoteDocumentNotFound()
                : File(document, "application/pdf", "orcamento.pdf", enableRangeProcessing: true);
        });
    }

    /// <summary>
    /// As cinco falhas que uma consulta de orcamento pode produzir, num lugar so. A fonte de
    /// orcamentos indisponivel, a fonte de etapas indisponivel e o documento ilegivel sao
    /// casos distintos de proposito: a primeira manda olhar o Salesforce, a segunda manda
    /// olhar o AutoLAB e a terceira manda olhar o arquivo. Uma excecao que servisse as tres
    /// mandaria quem atende procurar no lugar errado.
    /// </summary>
    [HttpPost("{id}/acceptance")]
    [ProducesResponseType(typeof(ClientDecisionResponse), StatusCodes.Status201Created)]
    [ProducesResponseType<ProblemDetails>(StatusCodes.Status400BadRequest)]
    [ProducesResponseType<ProblemDetails>(StatusCodes.Status404NotFound)]
    [ProducesResponseType<ProblemDetails>(StatusCodes.Status409Conflict)]
    public Task<IActionResult> RecordAcceptance(
        string id,
        [FromBody] AcceptanceTermsRequest request,
        CancellationToken cancellationToken)
    {
        if (!request.TryToTerms(out AcceptanceTerms terms, out string problem))
            return Task.FromResult(this.InvalidClientDecision(problem));

        return RecordAsync(
            id,
            ClientDecisionKind.Acceptance,
            author => new LactecClientDecisionRequest(ClientDecisionKind.Acceptance, author, Terms: terms),
            () => new BackendLabClientDecisionRequest(ClientDecisionKind.Acceptance, Terms: terms),
            cancellationToken);
    }

    [HttpPost("{id}/purchase-order")]
    [RequestSizeLimit(MaxPurchaseOrderBytes + 64 * 1024)]
    [RequestFormLimits(MultipartBodyLengthLimit = MaxPurchaseOrderBytes + 64 * 1024)]
    [ProducesResponseType(typeof(ClientDecisionResponse), StatusCodes.Status201Created)]
    [ProducesResponseType<ProblemDetails>(StatusCodes.Status400BadRequest)]
    [ProducesResponseType<ProblemDetails>(StatusCodes.Status404NotFound)]
    [ProducesResponseType<ProblemDetails>(StatusCodes.Status409Conflict)]
    public async Task<IActionResult> RecordPurchaseOrder(string id, IFormFile? file, CancellationToken cancellationToken)
    {
        if (file is null || file.Length == 0)
            return this.InvalidClientDecision("Envie a ordem de compra no campo file.");
        if (file.Length > MaxPurchaseOrderBytes)
            return this.InvalidClientDecision($"A ordem de compra aceita ate {MaxPurchaseOrderBytes / (1024 * 1024)} MB.");

        byte[] content;
        await using (Stream stream = file.OpenReadStream())
        using (MemoryStream buffer = new())
        {
            await stream.CopyToAsync(buffer, cancellationToken);
            content = buffer.ToArray();
        }

        string originalFileName = file.FileName.Trim();
        int lastSeparator = Math.Max(originalFileName.LastIndexOf('/'), originalFileName.LastIndexOf('\\'));
        string fileName = originalFileName[(lastSeparator + 1)..];
        if (fileName.Length is 0 or > 255) fileName = "ordem-de-compra.pdf";

        if (!PurchaseOrderFormats.Any(format =>
                fileName.EndsWith(format.Extension, StringComparison.OrdinalIgnoreCase)
                && content.AsSpan().StartsWith(format.Signature)))
            return this.InvalidClientDecision("A ordem de compra precisa ser um arquivo PDF, DOC ou DOCX válido.");

        PurchaseOrderFile purchaseOrder = new(fileName, content);

        return await RecordAsync(
            id,
            ClientDecisionKind.PurchaseOrder,
            author => new LactecClientDecisionRequest(ClientDecisionKind.PurchaseOrder, author, PurchaseOrder: purchaseOrder),
            () => new BackendLabClientDecisionRequest(ClientDecisionKind.PurchaseOrder, PurchaseOrder: purchaseOrder),
            cancellationToken);
    }

    [HttpPost("{id}/sample-shipment")]
    [ProducesResponseType(typeof(ClientDecisionResponse), StatusCodes.Status201Created)]
    [ProducesResponseType<ProblemDetails>(StatusCodes.Status404NotFound)]
    [ProducesResponseType<ProblemDetails>(StatusCodes.Status409Conflict)]
    public Task<IActionResult> RecordSampleShipment(string id, CancellationToken cancellationToken)
    {
        return RecordAsync(
            id,
            ClientDecisionKind.SampleShipment,
            author => new LactecClientDecisionRequest(ClientDecisionKind.SampleShipment, author),
            () => new BackendLabClientDecisionRequest(ClientDecisionKind.SampleShipment),
            cancellationToken);
    }

    [HttpPost("{id}/rejection")]
    [ProducesResponseType(typeof(ClientDecisionResponse), StatusCodes.Status201Created)]
    [ProducesResponseType<ProblemDetails>(StatusCodes.Status400BadRequest)]
    [ProducesResponseType<ProblemDetails>(StatusCodes.Status404NotFound)]
    [ProducesResponseType<ProblemDetails>(StatusCodes.Status409Conflict)]
    public Task<IActionResult> RecordRejection(string id, [FromBody] RejectionRequest? request, CancellationToken cancellationToken)
    {
        string justification = request?.Justification?.Trim() ?? string.Empty;

        if (justification.Length > RejectionRequest.MaxLength)
            return Task.FromResult(this.InvalidClientDecision($"A justificativa aceita ate {RejectionRequest.MaxLength} caracteres."));

        return RecordAsync(
            id,
            ClientDecisionKind.Rejection,
            author => new LactecClientDecisionRequest(ClientDecisionKind.Rejection, author, Justification: justification),
            () => new BackendLabClientDecisionRequest(ClientDecisionKind.Rejection, Justification: justification),
            cancellationToken);
    }

    private Task<IActionResult> RecordAsync(
        string id,
        ClientDecisionKind kind,
        Func<ClientDecisionAuthor, ClientDecisionRequest> buildLactec,
        Func<ClientDecisionRequest> buildBackendLab,
        CancellationToken cancellationToken)
    {
        bool isBackendLabAcceptance = _portal.DataSource == PortalOptions.BackendLabSource
            && kind is ClientDecisionKind.Acceptance or ClientDecisionKind.PurchaseOrder;
        bool isLactecDecision = _portal.DataSource == PortalOptions.LactecSource;
        if (!isBackendLabAcceptance && !isLactecDecision)
            return Task.FromResult(this.ClientDecisionsNotAvailable());

        ClientDecisionRequest request;
        if (isLactecDecision)
        {
            var user = PortalJwtService.ReadUser(User);
            ClientDecisionAuthor author = new(user.Id, user.Name, user.Email);
            request = buildLactec(author);
        }
        else
            request = buildBackendLab();

        return AnswerAsync(async () =>
        {
            ClientDecisionResult result = await _decisions.RecordAsync(id, request, cancellationToken);

            return result.Outcome switch
            {
                ClientDecisionOutcome.Recorded => StatusCode(
                    StatusCodes.Status201Created, ClientDecisionResponse.From(result)),
                ClientDecisionOutcome.QuoteNotFound => this.QuoteNotFound(),
                _ => this.ClientDecisionNotAllowed()
            };
        });
    }

    private async Task<IActionResult> AnswerAsync(Func<Task<IActionResult>> answer)
    {
        try
        {
            return await answer();
        }
        catch (QuoteAuthenticationRequiredException)
        {
            Response.Headers["WWW-Authenticate"] = "Bearer";
            return this.QuoteAuthenticationRequired();
        }
        catch (QuoteAccessForbiddenException)
        {
            return this.QuoteAccessForbidden();
        }
        catch (LaboratoryProgressSourceUnavailableException)
        {
            return this.LaboratoryProgressSourceUnavailable();
        }
        catch (ClientDecisionStoreUnavailableException failure)
        {
            return failure.Connected ? this.ClientDecisionStoreUnavailable() : this.ClientDecisionsNotAvailable();
        }
        catch (ClientDecisionRequestRejectedException failure)
        {
            return this.InvalidClientDecision(failure.Message);
        }
        catch (InvalidOperationException)
        {
            return this.QuoteRequestRefused();
        }
        catch (QuoteDocumentUnavailableException)
        {
            return this.QuoteDocumentUnavailable();
        }
        catch (Exception failure) when (failure is QuoteSourceUnavailableException or HttpRequestException)
        {
            return this.QuoteSourceUnavailable();
        }
        catch (OperationCanceledException) when (!HttpContext.RequestAborted.IsCancellationRequested)
        {
            return this.QuoteSourceUnavailable();
        }
    }
}
