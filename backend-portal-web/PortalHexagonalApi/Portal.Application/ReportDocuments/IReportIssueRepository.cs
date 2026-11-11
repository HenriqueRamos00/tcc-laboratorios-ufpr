using Portal.Application.Paging;
using Portal.Application.Visibility;

namespace Portal.Application.ReportDocuments;

public interface IReportIssueRepository
{
    Task<SignedReportDocument?> GetSignedDocumentOfAsync(
        string reportIdentification,
        CancellationToken cancellationToken);

    Task<IReadOnlySet<string>> GetIdentificationsWithSignedDocumentAsync(
        IReadOnlyCollection<string> reportIdentifications,
        CancellationToken cancellationToken);

    Task<ReportIssue?> GetCurrentIssueOfAsync(
        string reportIdentification,
        CancellationToken cancellationToken);

    Task<Page<LabReport>> ListReportsAsync(
        VisibilityScope scope,
        string? quoteCode,
        PageRequest page,
        CancellationToken cancellationToken);

    Task<SignedReportDocument?> GetSignedDocumentOfReportAsync(
        VisibilityScope scope,
        Guid reportId,
        CancellationToken cancellationToken);
}
