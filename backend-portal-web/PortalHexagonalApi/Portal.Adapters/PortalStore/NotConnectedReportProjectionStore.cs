using Portal.Application.LabResults;
using Portal.Application.Paging;
using Portal.Application.ReportProjection;

namespace Portal.Adapters.PortalStore;

public sealed class NotConnectedReportProjectionStore : IReportProjectionStore
{
    public bool IsConnected => false;

    public Task<IReadOnlyList<LoadScope>> GetActiveScopesAsync(CancellationToken cancellationToken) =>
        Task.FromResult<IReadOnlyList<LoadScope>>([]);

    public Task SaveProgressAsync(
        string cnpj,
        DateTime processedUntilIssueDate,
        Guid processedUntilReportId,
        CancellationToken cancellationToken) =>
        Task.CompletedTask;

    public Task SaveRunAsync(string cnpj, DateTime ranAt, CancellationToken cancellationToken) =>
        Task.CompletedTask;

    public Task<IReadOnlyList<ReadingControl>> GetControlsAsync(
        IReadOnlyCollection<Guid> signedFileIds,
        CancellationToken cancellationToken) =>
        Task.FromResult<IReadOnlyList<ReadingControl>>([]);

    public Task<IReadOnlyList<StoredReading>> GetUnreadableReadingsAsync(
        string cnpj,
        CancellationToken cancellationToken) =>
        Task.FromResult<IReadOnlyList<StoredReading>>([]);

    public Task<IReadOnlyList<StoredReading>> GetReadingsAsync(
        IReadOnlyCollection<string> cnpjs,
        IReadOnlyCollection<string> identifications,
        CancellationToken cancellationToken) =>
        Task.FromResult<IReadOnlyList<StoredReading>>([]);

    public Task<IReadOnlyList<ReadSummary>> GetReadSummariesAsync(
        string cnpj,
        IReadOnlyCollection<string>? identifications,
        CancellationToken cancellationToken) =>
        Task.FromResult<IReadOnlyList<ReadSummary>>([]);

    public Task SaveReadingAsync(StoredReading reading, CancellationToken cancellationToken) =>
        Task.CompletedTask;

    public Task SaveStatusesAsync(
        string cnpj,
        IReadOnlyCollection<EquipmentStatus> statuses,
        CancellationToken cancellationToken) =>
        Task.CompletedTask;

    public Task<IReadOnlyList<EquipmentStatus>> GetStatusesAsync(
        IReadOnlyCollection<string> cnpjs,
        IReadOnlyCollection<string> equipmentKeys,
        CancellationToken cancellationToken) =>
        Task.FromResult<IReadOnlyList<EquipmentStatus>>([]);

    public Task<Page<EquipmentStatus>> ListStatusesAsync(
        IReadOnlyCollection<string> cnpjs,
        OverallStatus status,
        EquipmentSearch? search,
        PageRequest page,
        CancellationToken cancellationToken) =>
        Task.FromResult(Page<EquipmentStatus>.Empty(page));
}
