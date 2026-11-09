using Portal.Application.Paging;
using Portal.Application.Visibility;

namespace Portal.Application.ReportDocuments;

public interface IForConsultingReports
{
    Task<Page<LabReport>> ListReportsAsync(
        VisibilityScope scope,
        string? quoteCode,
        PageRequest page,
        CancellationToken cancellationToken);

    Task<SignedReportDocument?> GetSignedDocumentAsync(
        VisibilityScope scope,
        Guid reportId,
        CancellationToken cancellationToken);
}
