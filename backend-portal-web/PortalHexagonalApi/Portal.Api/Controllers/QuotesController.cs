using Microsoft.AspNetCore.Mvc;
using Portal.Application.Common;
using Portal.Application.Quotes;
using Portal.Domain.Quotes;

namespace Portal.Api.Controllers;

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
    public async Task<ActionResult<IReadOnlyList<Quote>>> GetQuotes(CancellationToken cancellationToken)
    {
        try
        {
            IReadOnlyList<Quote> quotes = await _quoteUseCase.GetQuotesAsync(cancellationToken);
            return Ok(quotes);
        }
        catch (InvalidOperationException exception)
        {
            return BadRequest(new { message = exception.Message });
        }
        catch (ExternalServiceException exception)
        {
            return StatusCode(StatusCodes.Status502BadGateway, new { message = exception.Message });
        }
        catch (HttpRequestException exception)
        {
            return StatusCode(StatusCodes.Status502BadGateway, new { message = exception.Message });
        }
    }

    [HttpGet("{id}/document")]
    [ProducesResponseType(typeof(FileContentResult), StatusCodes.Status200OK, "application/pdf")]
    [ProducesResponseType(StatusCodes.Status404NotFound)]
    [ProducesResponseType(StatusCodes.Status502BadGateway)]
    public async Task<IActionResult> GetDocument(string id, CancellationToken cancellationToken)
    {
        try
        {
            byte[]? document = await _quoteUseCase.GetDocumentAsync(id, cancellationToken);
            if (document is null) return NotFound(new { message = "Quote document not found." });
            return File(document, "application/pdf", "orcamento.pdf", enableRangeProcessing: true);
        }
        catch (ExternalServiceException exception)
        {
            return StatusCode(StatusCodes.Status502BadGateway, new { message = exception.Message });
        }
        catch (HttpRequestException exception)
        {
            return StatusCode(StatusCodes.Status502BadGateway, new { message = exception.Message });
        }
    }

    [HttpGet("{id}")]
    public async Task<ActionResult<Quote>> GetQuoteById(string id, CancellationToken cancellationToken)
    {
        Quote? quote;

        try
        {
            quote = await _quoteUseCase.GetQuoteByIdAsync(id, cancellationToken);
        }
        catch (InvalidOperationException exception)
        {
            return BadRequest(new { message = exception.Message });
        }
        catch (ExternalServiceException exception)
        {
            return StatusCode(StatusCodes.Status502BadGateway, new { message = exception.Message });
        }
        catch (HttpRequestException exception)
        {
            return StatusCode(StatusCodes.Status502BadGateway, new { message = exception.Message });
        }

        if (quote is null)
            return NotFound(new { message = "Quote not found." });

        return Ok(quote);
    }
}
