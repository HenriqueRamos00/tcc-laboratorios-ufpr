using Portal.Application.Visibility;

namespace Portal.Application.ReportDocuments;

public interface IForDownloadingSignedReports
{
    Task<SignedReportDocument?> GetSignedReportAsync(
        CompanyScope scope,
        string equipmentKey,
        string protocoloLactec,
        CancellationToken cancellationToken);
}
