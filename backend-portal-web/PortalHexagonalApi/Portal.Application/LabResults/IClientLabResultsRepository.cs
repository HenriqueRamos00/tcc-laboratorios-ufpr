using Portal.Application.Paging;

namespace Portal.Application.LabResults;

/// <summary>
/// Leituras de equipamentos e publicações autorizadas pela fonte do cliente. A fonte aplica
/// a autorização com o Bearer encaminhado; este contrato não recebe uma empresa escolhida
/// pelo consumidor nem usa protocolo como identidade.
/// </summary>
public interface IClientLabResultsRepository
{
    Task<Page<ClientEquipment>> ListEquipmentAsync(PageRequest page, CancellationToken cancellationToken);

    Task<ClientEquipment?> GetEquipmentAsync(string equipmentId, CancellationToken cancellationToken);

    Task<Page<ClientAnalysis>?> ListAnalysesOfEquipmentAsync(
        string equipmentId,
        PageRequest page,
        CancellationToken cancellationToken);

    Task<ClientAnalysis?> GetAnalysisAsync(string reportId, CancellationToken cancellationToken);

    Task<ClientReportDetail?> GetReportDetailAsync(string reportId, CancellationToken cancellationToken);
}
