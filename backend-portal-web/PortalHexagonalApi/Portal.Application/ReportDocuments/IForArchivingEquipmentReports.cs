using Portal.Application.Visibility;

namespace Portal.Application.ReportDocuments;

public interface IForArchivingEquipmentReports
{
    Task<EquipmentReportArchive?> OpenArchiveAsync(
        CompanyScope scope,
        string equipmentKey,
        CancellationToken cancellationToken);
}
