using Microsoft.AspNetCore.Mvc;

using Portal.Api.Reports;
using Portal.Application.Quotes.Exceptions;
using Portal.Application.Reports;
using Portal.Application.Reports.Exceptions;
using Portal.Domain.Reports;

namespace Portal.Api.Controllers;

/// <summary>Consulta relatórios técnicos publicados para o cliente autenticado.</summary>
[ApiController]
[Route("api/reports")]
public sealed class ReportsController(IForConsultingReports reports) : ControllerBase
{
    [HttpGet]
    [ProducesResponseType(typeof(IReadOnlyList<PublishedReportResponse>), StatusCodes.Status200OK)]
    [ProducesResponseType<ProblemDetails>(StatusCodes.Status401Unauthorized)]
    [ProducesResponseType<ProblemDetails>(StatusCodes.Status403Forbidden)]
    [ProducesResponseType<ProblemDetails>(StatusCodes.Status502BadGateway)]
    public Task<IActionResult> List(CancellationToken cancellationToken) => AnswerAsync(async () =>
    {
        IReadOnlyList<PublishedReport> published = await reports.ListPublishedAsync(cancellationToken);
        return Ok(published.Select(PublishedReportResponse.From).ToArray());
    });

    [HttpGet("/api/quotes/{quoteId}/reports")]
    [ProducesResponseType(typeof(QuoteReportConclusionResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status204NoContent)]
    [ProducesResponseType<ProblemDetails>(StatusCodes.Status401Unauthorized)]
    [ProducesResponseType<ProblemDetails>(StatusCodes.Status403Forbidden)]
    [ProducesResponseType<ProblemDetails>(StatusCodes.Status404NotFound)]
    [ProducesResponseType<ProblemDetails>(StatusCodes.Status502BadGateway)]
    public Task<IActionResult> ListOfQuote(string quoteId, CancellationToken cancellationToken) => AnswerAsync(async () =>
    {
        QuoteReportConclusion? conclusion = await reports.GetPublishedOfQuoteAsync(quoteId, cancellationToken);
        return conclusion is null
            ? this.QuoteNotFound()
            : conclusion.Reports.Count == 0
                ? NoContent()
                : Ok(QuoteReportConclusionResponse.From(conclusion));
    });

    [HttpGet("{reportId}/file")]
    [ProducesResponseType(typeof(FileContentResult), StatusCodes.Status200OK, "application/pdf")]
    [ProducesResponseType<ProblemDetails>(StatusCodes.Status401Unauthorized)]
    [ProducesResponseType<ProblemDetails>(StatusCodes.Status403Forbidden)]
    [ProducesResponseType<ProblemDetails>(StatusCodes.Status404NotFound)]
    [ProducesResponseType<ProblemDetails>(StatusCodes.Status502BadGateway)]
    public Task<IActionResult> GetFile(string reportId, CancellationToken cancellationToken) => AnswerAsync(async () =>
    {
        PublishedReportFile? report = await reports.GetFileAsync(reportId, cancellationToken);
        if (report is null) return this.ReportDocumentNotFound();

        Response.Headers["Cache-Control"] = "no-store";
        return File(report.Content, "application/pdf", $"relatorio-{reportId}.pdf", enableRangeProcessing: true);
    });

    private async Task<IActionResult> AnswerAsync(Func<Task<IActionResult>> answer)
    {
        try
        {
            return await answer();
        }
        catch (ReportAuthenticationRequiredException)
        {
            Response.Headers.WWWAuthenticate = "Bearer";
            return this.ReportAuthenticationRequired();
        }
        catch (QuoteAuthenticationRequiredException)
        {
            Response.Headers.WWWAuthenticate = "Bearer";
            return this.ReportAuthenticationRequired();
        }
        catch (ReportAccessForbiddenException)
        {
            return this.ReportAccessForbidden();
        }
        catch (QuoteAccessForbiddenException)
        {
            return this.ReportAccessForbidden();
        }
        catch (ReportSourceUnavailableException)
        {
            return this.ReportSourceUnavailable();
        }
        catch (QuoteSourceUnavailableException)
        {
            return this.ReportSourceUnavailable();
        }
        catch (OperationCanceledException) when (!HttpContext.RequestAborted.IsCancellationRequested)
        {
            return this.ReportSourceUnavailable();
        }
    }
}
