using Microsoft.AspNetCore.Mvc;

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
[Route("api/quotes")]
public sealed class QuotesController : ControllerBase
{
    private readonly IQuoteUseCase _quoteUseCase;

    public QuotesController(IQuoteUseCase quoteUseCase)
    {
        _quoteUseCase = quoteUseCase;
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
        catch (InvalidOperationException failure)
        {
            return this.QuoteRequestRefused(failure.Message);
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
