using Portal.Adapters.AutoLab;
using Portal.Application.Nameplate;
using Portal.Application.Nameplate.Exceptions;
using Portal.Domain.Nameplate;

namespace Portal.Adapters.Nameplate;

public sealed class AutoLabEquipmentRegistryRepository : IEquipmentNameplateRepository
{
    private readonly AutoLabDbContext _database;
    private readonly AutoLabQueryLimits _limits;

    public AutoLabEquipmentRegistryRepository(AutoLabDbContext database, AutoLabQueryLimits limits)
    {
        _database = database;
        _limits = limits;
    }

    public async Task<IReadOnlyList<EquipmentNameplate>> GetNameplatesOfAsync(
        IReadOnlyCollection<string> serialNumbers,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(serialNumbers);

        string[] wanted = [.. serialNumbers
            .Where(serialNumber => !string.IsNullOrWhiteSpace(serialNumber))
            .Select(serialNumber => serialNumber.Trim())
            .Distinct(StringComparer.Ordinal)];

        if (wanted.Length == 0) return [];

        List<EquipamentosRecord> rows = await AutoLabFetch.BoundedListInChunksAsync(
            wanted,
            _limits,
            (chunk, rowLimit) => AutoLabQueries.RegistryRowsOf(_database, chunk, rowLimit),
            Unavailable,
            cancellationToken);

        return EquipmentRegistryReading.NameplatesOf(rows.Select(ToRow), wanted);
    }

    public async Task<EquipmentNameplate?> GetNameplateOfAsync(
        string serialNumber,
        CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(serialNumber)) return null;

        IReadOnlyList<EquipmentNameplate> found = await GetNameplatesOfAsync(
            [serialNumber], cancellationToken);

        return found.FirstOrDefault();
    }

    public static EquipmentRow ToRow(EquipamentosRecord record)
    {
        ArgumentNullException.ThrowIfNull(record);

        return new EquipmentRow
        {
            NumSerie = record.NumSerie,
            Tag = record.Tag,
            AnoFabricacao = record.AnoFabricacao,
            Potencia = record.Potencia,
            Tensao = record.Tensao,
            Fabricante = record.Fabricante,
            VolumeOleo = record.VolumeOleo,
            UnidadePotencia = record.UnidadePotencia,
            TipoDeOleo = record.TipoDeOleo,
            TipoEquipamento = record.TipoEquipamento
        };
    }

    private static NameplateSourceUnavailableException Unavailable(string message, Exception cause)
    {
        return new NameplateSourceUnavailableException(message, cause);
    }
}
