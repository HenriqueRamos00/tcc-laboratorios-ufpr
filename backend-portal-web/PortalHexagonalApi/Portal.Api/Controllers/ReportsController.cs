using Microsoft.AspNetCore.Mvc;

using Portal.Api.Configuration;
using Portal.Api.LabResults;
using Portal.Api.ReportDocuments;
using Portal.Api.Reports;
using Portal.Application.Paging;
using Portal.Application.Quotes;
using Portal.Application.Quotes.Exceptions;
using Portal.Application.ReportDocuments;
using Portal.Application.ReportDocuments.Exceptions;
using Portal.Application.Reports.Exceptions;
using Portal.Application.Visibility;
using Portal.Domain.Quotes;
using Portal.Domain.Reports;
using SpringReports = Portal.Application.Reports;

namespace Portal.Api.Controllers;

[ApiController]
[Route("api/reports")]
public sealed class ReportsController(
    IForConsultingReports autoLabReports,
    SpringReports.IForConsultingReports springReports,
    IQuoteRepository quotes,
    LabResultsOptions labResults,
    ReportDocumentsOptions documents,
    PortalOptions portal) : ControllerBase
{
    private bool UsesAutoLab => documents.Source == ReportDocumentsOptions.SqlServerSource;
    private bool UsesSpring => !UsesAutoLab && portal.DataSource == PortalOptions.BackendLabSource;

    [HttpGet]
    [ProducesResponseType(typeof(IReadOnlyList<ReportResponse>), StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status400BadRequest)]
    [ProducesResponseType(StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(StatusCodes.Status403Forbidden)]
    [ProducesResponseType(StatusCodes.Status502BadGateway)]
    public Task<IActionResult> List(
        [FromQuery] PageQuery paging,
        [FromQuery] string? quoteCode,
        CancellationToken cancellationToken)
    {
        if (!paging.TryToRequest(out PageRequest request))
            return Task.FromResult(this.InvalidPage());

        // No AutoLAB, quoteCode filtra pelo campo quoteCode do proprio relatorio (OrcamentoCRM
        // exato, dentro do escopo de visibilidade). Nao e o codigo publico do orcamento; o
        // vinculo orcamento -> relatorios passa por /api/quotes/{id}/reports.
        if (UsesAutoLab)
            return AnswerAutoLabAsync(async scope =>
            {
                Page<LabReport> page = await autoLabReports.ListReportsAsync(scope, quoteCode, request, cancellationToken);
                PageQuery.WriteHeaders(Response, page);
                return Ok(page.Items.Select(ReportResponse.From));
            });

        return AnswerAsync(async () =>
        {
            if (!UsesSpring) return this.ReportDocumentSourceUnavailable();
            IReadOnlyList<PublishedReport> published;
            if (quoteCode is null)
                published = await springReports.ListPublishedAsync(cancellationToken);
            else
            {
                Quote? quote = await FindAuthorizedQuoteByCodeAsync(quoteCode, cancellationToken);
                SpringReports.QuoteReportConclusion? conclusion = quote is null
                    ? null
                    : await springReports.GetPublishedOfQuoteAsync(quote.Id, cancellationToken);
                published = conclusion?.Reports ?? [];
            }
            Page<PublishedReport> page = Page<PublishedReport>.Of(published, request);
            PageQuery.WriteHeaders(Response, page);
            return Ok(page.Items.Select(ReportResponse.From));
        });
    }

    [HttpGet("/api/quotes/{quoteId}/reports")]
    [ProducesResponseType(typeof(QuoteReportConclusionResponse), StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status204NoContent)]
    [ProducesResponseType(StatusCodes.Status404NotFound)]
    public Task<IActionResult> ListOfQuote(
        string quoteId, [FromQuery] PageQuery paging, CancellationToken cancellationToken)
    {
        if (!paging.TryToRequest(out PageRequest request))
            return Task.FromResult(this.InvalidPage());

        if (UsesAutoLab)
            return AnswerAutoLabAsync(async scope =>
            {
                // Resolve the authorized quote first; an arbitrary quote code must not select another quote's reports.
                Quote? quote = await quotes.GetQuoteByIdAsync(quoteId, cancellationToken);
                if (quote is null) return this.QuoteNotFound();
                // O AutoLAB conhece o orcamento pela chave de laboratorio, e nao pelo codigo publico.
                // Sem chave valida nao ha relatorio a pedir, e o AutoLAB nem e consultado.
                Page<LabReport> page = quote.LaboratoryKey is { } key
                    ? await autoLabReports.ListReportsAsync(scope, key.Value, request, cancellationToken)
                    : Page<LabReport>.Empty(request);
                PageQuery.WriteHeaders(Response, page);
                return page.TotalCount == 0
                    ? NoContent()
                    : Ok(new QuoteReportConclusionResponse(
                        quote.Id, quote.Code, quote.Protocol ?? string.Empty, string.Empty, string.Empty,
                        page.Items.Select(ReportResponse.From).ToArray()));
            });

        return AnswerAsync(async () =>
        {
            if (!UsesSpring) return this.ReportDocumentSourceUnavailable();
            SpringReports.QuoteReportConclusion? conclusion = await springReports.GetPublishedOfQuoteAsync(quoteId, cancellationToken);
            if (conclusion is null) return this.QuoteNotFound();
            Page<PublishedReport> page = Page<PublishedReport>.Of(conclusion.Reports, request);
            PageQuery.WriteHeaders(Response, page);
            return page.TotalCount == 0
                ? NoContent()
                : Ok(QuoteReportConclusionResponse.From(conclusion) with
                {
                    Reports = page.Items.Select(report => ReportResponse.From(report) with { QuoteCode = conclusion.QuoteCode }).ToArray()
                });
        });
    }

    [HttpGet("{id}/document")]
    [HttpGet("{id}/file")]
    [ProducesResponseType(typeof(FileContentResult), StatusCodes.Status200OK, "application/pdf")]
    [ProducesResponseType(StatusCodes.Status400BadRequest)]
    [ProducesResponseType(StatusCodes.Status404NotFound)]
    [ProducesResponseType(StatusCodes.Status502BadGateway)]
    public Task<IActionResult> GetDocument(string id, CancellationToken cancellationToken)
    {
        if (UsesAutoLab)
        {
            if (!OpaqueIdentifier.TryDecode(id, out string decoded) || !Guid.TryParse(decoded, out Guid reportId))
                return Task.FromResult(this.UnreadableReportIdentifier());
            return AnswerAutoLabAsync(async scope =>
            {
                SignedReportDocument? document = await autoLabReports.GetSignedDocumentAsync(scope, reportId, cancellationToken);
                Response.Headers.CacheControl = "no-store";
                return document is null
                    ? this.ReportDocumentNotFound()
                    : File(document.Content, "application/pdf", SignedDocumentName.Of(document));
            });
        }
        return AnswerAsync(async () =>
        {
            if (!UsesSpring) return this.ReportDocumentSourceUnavailable();
            PublishedReportFile? document = await springReports.GetFileAsync(id, cancellationToken);
            Response.Headers.CacheControl = "no-store";
            return document is null
                ? this.ReportDocumentNotFound()
                : File(document.Content, "application/pdf", $"relatorio-{id}.pdf", enableRangeProcessing: true);
        });
    }

    /// <summary>
    /// O orcamento autorizado com este codigo publico, procurado pagina a pagina na fonte
    /// comercial. A busca para no primeiro encontrado; a lista inteira so e percorrida quando
    /// o codigo nao pertence ao cliente.
    /// </summary>
    private async Task<Quote?> FindAuthorizedQuoteByCodeAsync(string quoteCode, CancellationToken cancellationToken)
    {
        for (int number = 1; number <= PageRequest.MaxPage; number++)
        {
            Page<Quote> page = await quotes.GetQuotesAsync(
                new PageRequest(number, PageRequest.MaxPageSize), cancellationToken);
            Quote? quote = page.Items.FirstOrDefault(item => string.Equals(item.Code, quoteCode, StringComparison.Ordinal));
            if (quote is not null || !page.HasNext || page.Items.Count == 0) return quote;
        }

        return null;
    }

    private Task<IActionResult> AnswerAutoLabAsync(Func<VisibilityScope, Task<IActionResult>> answer)
    {
        VisibilityScope? scope = VisibilityScopeResolution.Resolve(User, labResults);
        return scope is null ? Task.FromResult(this.TenantNotIdentified()) : AnswerAsync(() => answer(scope));
    }

    private async Task<IActionResult> AnswerAsync(Func<Task<IActionResult>> answer)
    {
        try { return await answer(); }
        catch (ReportAuthenticationRequiredException) { return AuthenticationRequired(); }
        catch (QuoteAuthenticationRequiredException) { return AuthenticationRequired(); }
        catch (ReportAccessForbiddenException) { return this.ReportAccessForbidden(); }
        catch (QuoteAccessForbiddenException) { return this.ReportAccessForbidden(); }
        catch (ReportSourceUnavailableException) { return this.ReportSourceUnavailable(); }
        catch (QuoteSourceUnavailableException) { return this.ReportSourceUnavailable(); }
        catch (ReportDocumentSourceUnavailableException) { return this.ReportDocumentSourceUnavailable(); }
        catch (OperationCanceledException) when (!HttpContext.RequestAborted.IsCancellationRequested)
        { return this.ReportSourceUnavailable(); }
    }

    private IActionResult AuthenticationRequired()
    {
        Response.Headers.WWWAuthenticate = "Bearer";
        return this.ReportAuthenticationRequired();
    }
}
