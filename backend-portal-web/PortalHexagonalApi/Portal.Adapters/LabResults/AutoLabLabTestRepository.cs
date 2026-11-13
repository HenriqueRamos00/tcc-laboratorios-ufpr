using Portal.Adapters.AutoLab;
using Portal.Application.LabResults;
using Portal.Application.LabResults.Exceptions;
using Portal.Application.Paging;
using Portal.Application.Visibility;
using Portal.Domain.LabResults;

namespace Portal.Adapters.LabResults;

public sealed class AutoLabLabTestRepository : ILabTestRepository
{
    private readonly AutoLabDbContext _database;
    private readonly AutoLabQueryLimits _limits;

    public AutoLabLabTestRepository(AutoLabDbContext database, AutoLabQueryLimits limits)
    {
        _database = database;
        _limits = limits;
    }

    public async Task<Page<SampleAnalysis>> GetAnalysesAsync(
        VisibilityScope scope,
        PageRequest equipmentPage,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(scope);
        ArgumentNullException.ThrowIfNull(equipmentPage);

        Page<string> keys = await AutoLabFetch.GuardAsync(
            () => AutoLabQueries.EquipmentKeys(_database, scope)
                .ToPageAsync(equipmentPage, cancellationToken),
            Unavailable);

        if (keys.Items.Count == 0) return keys.WithItems<SampleAnalysis>([]);

        List<VwDadosEnsaiosRecord> rows = await AutoLabFetch.BoundedListAsync(
            AutoLabQueries.RowsOfEquipmentKeys(
                _database, scope, [.. keys.Items], _limits.MaxRowsPerQuery + 1),
            _limits.MaxRowsPerQuery,
            Unavailable,
            cancellationToken);

        HashSet<string> onThePage = new(keys.Items.Select(key => key.Trim()), StringComparer.Ordinal);

        return keys.WithItems<SampleAnalysis>(
            [.. AnalysesIn(rows, scope)
                .Where(analysis => onThePage.Contains(analysis.EquipmentKey))]);
    }

    public async Task<Page<SampleAnalysis>> GetAnalysesOfEquipmentAsync(
        VisibilityScope scope,
        string equipmentKey,
        PageRequest reportPage,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(scope);
        ArgumentNullException.ThrowIfNull(reportPage);

        if (string.IsNullOrWhiteSpace(equipmentKey)) return Page<SampleAnalysis>.Empty(reportPage);

        string wantedKey = equipmentKey.Trim();

        Page<string> protocols = await AutoLabFetch.GuardAsync(
            () => AutoLabQueries.ProtocolsOfEquipment(_database, scope, wantedKey)
                .ToPageAsync(reportPage, cancellationToken),
            Unavailable);

        if (protocols.Items.Count == 0) return protocols.WithItems<SampleAnalysis>([]);

        List<VwDadosEnsaiosRecord> rows = await AutoLabFetch.BoundedListAsync(
            AutoLabQueries.RowsOfProtocols(
                _database, scope, [.. protocols.Items], _limits.MaxRowsPerQuery + 1),
            _limits.MaxRowsPerQuery,
            Unavailable,
            cancellationToken);

        return protocols.WithItems<SampleAnalysis>(
            [.. AnalysesIn(rows, scope)
                .Where(analysis => string.Equals(analysis.EquipmentKey, wantedKey, StringComparison.Ordinal))
                .OrderByDescending(analysis => analysis.AnalysisEndDate)
                .ThenBy(analysis => analysis.ProtocoloLactec, StringComparer.Ordinal)]);
    }

    public async Task<SampleAnalysis?> GetAnalysisByProtocolAsync(
        VisibilityScope scope,
        string protocoloLactec,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(scope);

        if (string.IsNullOrWhiteSpace(protocoloLactec)) return null;

        string wantedProtocol = protocoloLactec.Trim();

        List<VwDadosEnsaiosRecord> rows = await AutoLabFetch.BoundedListAsync(
            AutoLabQueries.RowsOfProtocol(
                _database, scope, wantedProtocol, _limits.MaxRowsPerQuery + 1),
            _limits.MaxRowsPerQuery,
            Unavailable,
            cancellationToken);

        return AnalysesIn(rows, scope)
            .FirstOrDefault(analysis => string.Equals(
                analysis.ProtocoloLactec, wantedProtocol, StringComparison.Ordinal));
    }

    public static IReadOnlyList<SampleAnalysis> BuildAnalyses(IEnumerable<VwDadosEnsaiosRecord> rows)
    {
        ArgumentNullException.ThrowIfNull(rows);

        return [.. rows
            .GroupBy(row => row.ProtocoloLactec ?? string.Empty, StringComparer.Ordinal)
            .Select(BuildOneAnalysis)];
    }

    private static SampleAnalysis BuildOneAnalysis(IGrouping<string, VwDadosEnsaiosRecord> rowsOfProtocol)
    {
        VwDadosEnsaiosRecord first = rowsOfProtocol.First();

        IReadOnlyList<Measurement> measurements = MeasuredItemRecognition.Identify(
            rowsOfProtocol.Select(row => new ResultBeforeRecognition(
                row.Name ?? string.Empty,
                MeasuredValue.InterpretResult(row.Resultado))));

        return new SampleAnalysis(
            rowsOfProtocol.Key,
            first.Identificacao ?? string.Empty,
            first.NumSerie,
            first.EmpresaTrafo ?? string.Empty,
            first.DataEmissao ?? default,
            first.DataFinalAnalise ?? default,
            measurements);
    }

    private static IEnumerable<SampleAnalysis> AnalysesIn(
        IEnumerable<VwDadosEnsaiosRecord> rows,
        VisibilityScope scope)
    {
        return BuildAnalyses(rows.Where(row => scope.Includes(row.EmpresaTrafo)))
            .Where(analysis => scope.Includes(analysis.Company));
    }

    private static LabTestSourceUnavailableException Unavailable(string message, Exception cause)
    {
        return new LabTestSourceUnavailableException(message, cause);
    }
}
