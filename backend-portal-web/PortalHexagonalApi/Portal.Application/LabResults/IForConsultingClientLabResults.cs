using Portal.Application.Paging;
using Portal.Domain.LabResults;

namespace Portal.Application.LabResults;

/// <summary>Casos de consulta do catálogo e das análises publicadas para o cliente.</summary>
public interface IForConsultingClientLabResults
{
    Task<Page<ClientEquipment>> ListEquipmentAsync(
        EquipmentSearch? search, PageRequest page, CancellationToken cancellationToken);

    Task<ClientEquipment?> GetEquipmentAsync(string equipmentId, CancellationToken cancellationToken);

    Task<Page<ClientAnalysis>?> ListAnalysesOfEquipmentAsync(
        string equipmentId,
        PageRequest page,
        CancellationToken cancellationToken);

    Task<ClientReportDetail?> GetReportDetailAsync(string reportId, CancellationToken cancellationToken);

    Task<Stream?> GetReportFileAsync(
        string equipmentId,
        string reportId,
        CancellationToken cancellationToken);

    Task<SampleHealthIndicators?> GetHealthIndicatorsAsync(
        string equipmentId,
        string? selectedAnalysisId,
        CancellationToken cancellationToken);
}
