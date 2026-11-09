using Microsoft.AspNetCore.Mvc;

using Portal.Api.LabResults;
using Portal.Api.ReportDocuments;
using Portal.Api.Reports;
using Portal.Application.Paging;
using Portal.Application.ReportDocuments;
using Portal.Application.ReportDocuments.Exceptions;
using Portal.Application.Visibility;

namespace Portal.Api.Controllers;

[ApiController]
[Route("api/reports")]
public sealed class ReportsController : ControllerBase
{
    private readonly IForConsultingReports _reports;
    private readonly LabResultsOptions _labResultsOptions;

    public ReportsController(IForConsultingReports reports, LabResultsOptions labResultsOptions)
    {
        _reports = reports;
        _labResultsOptions = labResultsOptions;
    }

    [HttpGet]
    [ProducesResponseType(typeof(IReadOnlyList<ReportResponse>), StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status400BadRequest)]
    [ProducesResponseType(StatusCodes.Status401Unauthorized)]
    [ProducesResponseType(StatusCodes.Status502BadGateway)]
    public Task<IActionResult> List(
        [FromQuery] PageQuery paging,
        [FromQuery] string? quoteCode,
        CancellationToken cancellationToken)
    {
        if (!paging.TryToRequest(out PageRequest request))
            return Task.FromResult(this.InvalidPage());

        return AnswerAsync(async scope =>
        {
            Page<LabReport> reports = await _reports
                .ListReportsAsync(scope, quoteCode, request, cancellationToken);

            PageQuery.WriteHeaders(Response, reports);

            return Ok(reports.Items.Select(report => new ReportResponse
            {
                Id = OpaqueIdentifier.Encode(report.Id.ToString("D")),
                Identification = report.Identification,
                RequestNumber = report.RequestNumber,
                QuoteCode = report.QuoteCode,
                SampleId = report.SampleId,
                SampleName = report.SampleName,
                Client = report.Client,
                Status = report.Status,
                ScheduledDate = WireDateOf(report.ScheduledDate),
                IssueDate = WireDateOf(report.IssueDate),
                PublicationDate = WireDateOf(report.PublicationDate),
                HasSignedDocument = report.HasSignedDocument
            }));
        });
    }

    [HttpGet("{id}/document")]
    [ProducesResponseType(typeof(FileContentResult), StatusCodes.Status200OK, "application/pdf")]
    [ProducesResponseType(StatusCodes.Status400BadRequest)]
    [ProducesResponseType(StatusCodes.Status404NotFound)]
    [ProducesResponseType(StatusCodes.Status502BadGateway)]
    public Task<IActionResult> GetSignedDocument(string id, CancellationToken cancellationToken)
    {
        if (!OpaqueIdentifier.TryDecode(id, out string decoded) || !Guid.TryParse(decoded, out Guid reportId))
            return Task.FromResult(this.UnreadableReportIdentifier());

        return AnswerAsync(async scope =>
        {
            SignedReportDocument? document = await _reports
                .GetSignedDocumentAsync(scope, reportId, cancellationToken);

            return document is null
                ? this.ReportDocumentNotFound()
                : File(document.Content, "application/pdf", SignedDocumentName.Of(document));
        });
    }

    private async Task<IActionResult> AnswerAsync(Func<VisibilityScope, Task<IActionResult>> answer)
    {
        VisibilityScope? scope = VisibilityScopeResolution.Resolve(User, _labResultsOptions);

        if (scope is null) return this.TenantNotIdentified();

        try
        {
            return await answer(scope);
        }
        catch (ReportDocumentSourceUnavailableException)
        {
            return this.ReportDocumentSourceUnavailable();
        }
    }

    private static string? WireDateOf(DateTime? moment)
    {
        return moment is null ? null : WireDate.From(moment.Value);
    }
}
