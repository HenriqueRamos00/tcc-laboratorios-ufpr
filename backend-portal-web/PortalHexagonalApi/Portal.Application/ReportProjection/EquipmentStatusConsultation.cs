using Portal.Application.LabResults;
using Portal.Application.Paging;
using Portal.Application.ReportProjection.Exceptions;
using Portal.Application.Visibility;

namespace Portal.Application.ReportProjection;

public interface IForConsultingEquipmentStatus
{
    Task<Page<EquipmentStatus>> ListAsync(
        CompanyScope scope,
        OverallStatus status,
        EquipmentSearch? search,
        PageRequest page,
        CancellationToken cancellationToken);

    Task<IReadOnlyDictionary<string, EquipmentStatus>> OfEquipmentAsync(
        CompanyScope scope,
        IReadOnlyCollection<string> equipmentKeys,
        CancellationToken cancellationToken);
}

public sealed class EquipmentStatusConsultation : IForConsultingEquipmentStatus
{
    private readonly IReportProjectionStore _store;

    public EquipmentStatusConsultation(IReportProjectionStore store)
    {
        _store = store;
    }

    public Task<Page<EquipmentStatus>> ListAsync(
        CompanyScope scope,
        OverallStatus status,
        EquipmentSearch? search,
        PageRequest page,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(scope);
        ArgumentNullException.ThrowIfNull(page);

        return scope.IsEmpty
            ? Task.FromResult(Page<EquipmentStatus>.Empty(page))
            : _store.ListStatusesAsync(scope.Cnpjs, status, search, page, cancellationToken);
    }

    public async Task<IReadOnlyDictionary<string, EquipmentStatus>> OfEquipmentAsync(
        CompanyScope scope,
        IReadOnlyCollection<string> equipmentKeys,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(scope);
        ArgumentNullException.ThrowIfNull(equipmentKeys);

        if (scope.IsEmpty || equipmentKeys.Count == 0 || !_store.IsConnected)
            return new Dictionary<string, EquipmentStatus>(StringComparer.Ordinal);

        try
        {
            return (await _store.GetStatusesAsync(scope.Cnpjs, equipmentKeys, cancellationToken))
                .GroupBy(status => status.Equipment.EquipmentKey, StringComparer.Ordinal)
                .ToDictionary(
                    found => found.Key,
                    found => found.OrderByDescending(status => status.LastReportDate).First(),
                    StringComparer.Ordinal);
        }
        catch (ReportProjectionUnavailableException)
        {
            return new Dictionary<string, EquipmentStatus>(StringComparer.Ordinal);
        }
    }
}
