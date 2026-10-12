using Microsoft.AspNetCore.Mvc;

using Portal.Application.Quotes;
using Portal.Application.Quotes.Exceptions;
using Portal.Domain.Quotes;

namespace Portal.Api.Controllers;

/// <summary>
/// Os tres caminhos de orcamento. O controller resolve o endereco, chama a porta condutora
/// e traduz; nenhuma regra de orcamento passa por aqui.
///
/// As quatro falhas possiveis eram tratadas em cada metodo, com o mesmo bloco repetido tres
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

    [HttpGet]
    [ProducesResponseType(typeof(IReadOnlyList<Quote>), StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status502BadGateway)]
    public Task<IActionResult> GetQuotes(CancellationToken cancellationToken)
    {
        return AnswerAsync(async () =>
        {
            IReadOnlyList<Quote> quotes = await _quoteUseCase.GetQuotesAsync(cancellationToken);

            return Ok(quotes);
        });
    }

    [HttpGet("{id}")]
    [ProducesResponseType(typeof(Quote), StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status404NotFound)]
    public Task<IActionResult> GetQuoteById(string id, CancellationToken cancellationToken)
    {
        return AnswerAsync(async () =>
        {
            Quote? quote = await _quoteUseCase.GetQuoteByIdAsync(id, cancellationToken);

            return quote is null ? this.QuoteNotFound() : Ok(quote);
        });
    }

    [HttpGet("{id}/document")]
    [ProducesResponseType(typeof(FileContentResult), StatusCodes.Status200OK, "application/pdf")]
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
    /// As quatro falhas que uma consulta de orcamento pode produzir, num lugar so. A fonte
    /// indisponivel e o documento ilegivel sao casos distintos de proposito: o primeiro manda
    /// olhar o Salesforce, o segundo manda olhar o arquivo.
    /// </summary>
    private async Task<IActionResult> AnswerAsync(Func<Task<IActionResult>> answer)
    {
        try
        {
            return await answer();
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
    }
}
