using Portal.Application.LabResults;
using Portal.Application.Paging;

namespace Portal.Application.ReportProjection;

public interface IReportProjectionStore
{
    bool IsConnected { get; }

    Task<IReadOnlyList<LoadScope>> GetActiveScopesAsync(CancellationToken cancellationToken);

    Task SaveProgressAsync(
        string cnpj,
        DateTime processedUntilIssueDate,
        Guid processedUntilReportId,
        CancellationToken cancellationToken);

    Task SaveRunAsync(string cnpj, DateTime ranAt, CancellationToken cancellationToken);

    Task<IReadOnlyList<ReadingControl>> GetControlsAsync(
        IReadOnlyCollection<Guid> signedFileIds,
        CancellationToken cancellationToken);

    Task<IReadOnlyList<StoredReading>> GetUnreadableReadingsAsync(
        string cnpj,
        CancellationToken cancellationToken);

    Task<IReadOnlyList<StoredReading>> GetReadingsAsync(
        IReadOnlyCollection<string> cnpjs,
        IReadOnlyCollection<string> identifications,
        CancellationToken cancellationToken);

    Task<IReadOnlyList<ReadSummary>> GetReadSummariesAsync(
        string cnpj,
        IReadOnlyCollection<string>? identifications,
        CancellationToken cancellationToken);

    Task SaveReadingAsync(StoredReading reading, CancellationToken cancellationToken);

    Task SaveStatusesAsync(
        string cnpj,
        IReadOnlyCollection<EquipmentStatus> statuses,
        CancellationToken cancellationToken);

    Task<IReadOnlyList<EquipmentStatus>> GetStatusesAsync(
        IReadOnlyCollection<string> cnpjs,
        IReadOnlyCollection<string> equipmentKeys,
        CancellationToken cancellationToken);

    Task<Page<EquipmentStatus>> ListStatusesAsync(
        IReadOnlyCollection<string> cnpjs,
        OverallStatus status,
        EquipmentSearch? search,
        PageRequest page,
        CancellationToken cancellationToken);
}
