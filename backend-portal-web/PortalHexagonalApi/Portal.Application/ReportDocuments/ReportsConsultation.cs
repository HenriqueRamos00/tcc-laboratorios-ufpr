using Portal.Application.Paging;
using Portal.Application.Visibility;

namespace Portal.Application.ReportDocuments;

public sealed class ReportsConsultation : IForConsultingReports
{
    private readonly IReportIssueRepository _reportIssues;

    public ReportsConsultation(IReportIssueRepository reportIssues)
    {
        _reportIssues = reportIssues;
    }

    public Task<Page<LabReport>> ListReportsAsync(
        VisibilityScope scope,
        string? quoteCode,
        PageRequest page,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(scope);
        ArgumentNullException.ThrowIfNull(page);

        return _reportIssues.ListReportsAsync(
            scope,
            string.IsNullOrWhiteSpace(quoteCode) ? null : quoteCode.Trim(),
            page,
            cancellationToken);
    }

    public Task<SignedReportDocument?> GetSignedDocumentAsync(
        VisibilityScope scope,
        Guid reportId,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(scope);

        return reportId == Guid.Empty
            ? Task.FromResult<SignedReportDocument?>(null)
            : _reportIssues.GetSignedDocumentOfReportAsync(scope, reportId, cancellationToken);
    }
}
