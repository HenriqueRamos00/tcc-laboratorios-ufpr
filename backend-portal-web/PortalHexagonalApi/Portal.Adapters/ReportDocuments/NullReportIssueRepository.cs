using Portal.Application.Paging;
using Portal.Application.ReportDocuments;
using Portal.Application.Visibility;

namespace Portal.Adapters.ReportDocuments;

public sealed class NullReportIssueRepository : IReportIssueRepository
{
    public Task<SignedReportDocument?> GetSignedDocumentOfAsync(
        string reportIdentification,
        CancellationToken cancellationToken)
    {
        return Task.FromResult<SignedReportDocument?>(null);
    }

    public Task<IReadOnlySet<string>> GetIdentificationsWithSignedDocumentAsync(
        IReadOnlyCollection<string> reportIdentifications,
        CancellationToken cancellationToken)
    {
        return Task.FromResult<IReadOnlySet<string>>(new HashSet<string>(StringComparer.Ordinal));
    }

    public Task<ReportIssue?> GetCurrentIssueOfAsync(
        string reportIdentification,
        CancellationToken cancellationToken)
    {
        return Task.FromResult<ReportIssue?>(null);
    }

    public Task<Page<LabReport>> ListReportsAsync(
        VisibilityScope scope,
        string? quoteCode,
        PageRequest page,
        CancellationToken cancellationToken)
    {
        return Task.FromResult(Page<LabReport>.Empty(page));
    }

    public Task<SignedReportDocument?> GetSignedDocumentOfReportAsync(
        VisibilityScope scope,
        Guid reportId,
        CancellationToken cancellationToken)
    {
        return Task.FromResult<SignedReportDocument?>(null);
    }
}
