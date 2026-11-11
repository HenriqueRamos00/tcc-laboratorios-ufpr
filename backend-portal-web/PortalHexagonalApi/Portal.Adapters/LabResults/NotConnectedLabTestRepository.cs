using Portal.Application.LabResults;
using Portal.Application.LabResults.Exceptions;
using Portal.Application.Paging;
using Portal.Application.Visibility;
using Portal.Domain.LabResults;

namespace Portal.Adapters.LabResults;

/// <summary>Explicitly disconnected until the client laboratory endpoints exist in Spring.</summary>
public sealed class NotConnectedLabTestRepository : ILabTestRepository
{
    public Task<Page<SampleAnalysis>> GetAnalysesAsync(
        VisibilityScope scope, PageRequest equipmentPage, CancellationToken cancellationToken) =>
        throw Pending();

    public Task<Page<SampleAnalysis>> GetAnalysesOfEquipmentAsync(
        VisibilityScope scope, string equipmentKey, PageRequest reportPage, CancellationToken cancellationToken) =>
        throw Pending();

    public Task<SampleAnalysis?> GetAnalysisByProtocolAsync(
        VisibilityScope scope, string protocoloLactec, CancellationToken cancellationToken) =>
        throw Pending();

    private static LabTestSourceUnavailableException Pending() =>
        new("A integração de equipamentos e análises do cliente com o Spring ainda não está disponível.");
}
