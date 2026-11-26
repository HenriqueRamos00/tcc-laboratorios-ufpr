using Portal.Application.LabResults;
using Portal.Application.LabResults.Exceptions;
using Portal.Application.Paging;
using Portal.Application.Visibility;
using Portal.Domain.LabResults;

namespace Portal.Adapters.LabResults;

/// <summary>
/// Explicitamente desconectado enquanto os endpoints de laboratorio do cliente nao
/// existirem no Spring.
/// </summary>
public sealed class NotConnectedLabTestRepository : ILabTestRepository
{
    public Task<Page<SampleAnalysis>> GetAnalysesAsync(
        CompanyScope scope,
        EquipmentSearch? search,
        PageRequest equipmentPage,
        CancellationToken cancellationToken) =>
        throw Pending();

    public Task<Page<SampleAnalysis>> GetAnalysesOfEquipmentAsync(
        CompanyScope scope, string equipmentKey, PageRequest reportPage, CancellationToken cancellationToken) =>
        throw Pending();

    public Task<SampleAnalysis?> GetAnalysisByProtocolAsync(
        CompanyScope scope, string protocoloLactec, CancellationToken cancellationToken) =>
        throw Pending();

    private static LabTestSourceUnavailableException Pending() =>
        new("A integração de equipamentos e análises do cliente com o Spring ainda não está disponível.");
}
