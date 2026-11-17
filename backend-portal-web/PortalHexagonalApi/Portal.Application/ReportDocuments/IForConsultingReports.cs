using Portal.Application.Paging;
using Portal.Application.Visibility;

namespace Portal.Application.ReportDocuments;

public interface IForConsultingReports
{
    Task<Page<LabReport>> ListReportsAsync(
        CompanyScope scope,
        string? quoteCode,
        PageRequest page,
        CancellationToken cancellationToken);

    Task<SignedReportDocument?> GetSignedDocumentAsync(
        CompanyScope scope,
        Guid reportId,
        CancellationToken cancellationToken);
}
