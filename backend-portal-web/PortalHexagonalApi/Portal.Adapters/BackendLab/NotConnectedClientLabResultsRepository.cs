using Portal.Application.LabResults;
using Portal.Application.LabResults.Exceptions;
using Portal.Application.Paging;

namespace Portal.Adapters.BackendLab;

/// <summary>Fecha o adapter cliente quando a fonte configurada não é o Backend Lab.</summary>
public sealed class NotConnectedClientLabResultsRepository : IClientLabResultsRepository
{
    private static ClientLabSourceUnavailableException Unavailable() =>
        new("A fonte de resultados de cliente não está conectada.");

    public Task<Page<ClientEquipment>> ListEquipmentAsync(PageRequest page, CancellationToken cancellationToken) =>
        Task.FromException<Page<ClientEquipment>>(Unavailable());

    public Task<ClientEquipment?> GetEquipmentAsync(string equipmentId, CancellationToken cancellationToken) =>
        Task.FromException<ClientEquipment?>(Unavailable());

    public Task<Page<ClientAnalysis>?> ListAnalysesOfEquipmentAsync(
        string equipmentId, PageRequest page, CancellationToken cancellationToken) =>
        Task.FromException<Page<ClientAnalysis>?>(Unavailable());

    public Task<ClientAnalysis?> GetAnalysisAsync(string reportId, CancellationToken cancellationToken) =>
        Task.FromException<ClientAnalysis?>(Unavailable());

    public Task<ClientReportDetail?> GetReportDetailAsync(string reportId, CancellationToken cancellationToken) =>
        Task.FromException<ClientReportDetail?>(Unavailable());

}
