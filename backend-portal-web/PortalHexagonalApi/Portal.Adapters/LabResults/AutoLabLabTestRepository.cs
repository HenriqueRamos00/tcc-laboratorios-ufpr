using Portal.Adapters.AutoLab;
using Portal.Application.CompanyRecords;
using Portal.Application.CompanyRecords.Exceptions;
using Portal.Application.LabResults;
using Portal.Application.LabResults.Exceptions;
using Portal.Application.Paging;
using Portal.Application.Visibility;
using Portal.Domain.LabResults;

namespace Portal.Adapters.LabResults;

/// <summary>
/// Le os ensaios de <c>vwDadosEnsaios</c> somente pelos protocolos que
/// <see cref="ICompanyRecordsSource"/> entregou para o escopo. O nome da empresa na linha
/// (<c>EmpresaTrafo</c>) e texto de exibicao e nao participa de filtro nenhum. Sem protocolo
/// autorizado a view nem e consultada.
/// </summary>
public sealed class AutoLabLabTestRepository : ILabTestRepository
{
    private readonly AutoLabDbContext _database;
    private readonly ICompanyRecordsSource _companyRecords;
    private readonly AutoLabQueryLimits _limits;

    public AutoLabLabTestRepository(
        AutoLabDbContext database,
        ICompanyRecordsSource companyRecords,
        AutoLabQueryLimits limits)
    {
        _database = database;
        _companyRecords = companyRecords;
        _limits = limits;
    }

    public async Task<Page<SampleAnalysis>> GetAnalysesAsync(
        CompanyScope scope,
        EquipmentSearch? search,
        PageRequest equipmentPage,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(scope);
        ArgumentNullException.ThrowIfNull(equipmentPage);

        List<string> authorized = await AuthorizedProtocolsAsync(scope, cancellationToken);

        if (authorized.Count == 0) return Page<SampleAnalysis>.Empty(equipmentPage);

        Page<string> keys = await AutoLabFetch.GuardAsync(
            () => AutoLabQueries.EquipmentKeys(_database, authorized, search)
                .ToPageAsync(equipmentPage, cancellationToken),
            Unavailable);

        if (keys.Items.Count == 0) return keys.WithItems<SampleAnalysis>([]);

        List<VwDadosEnsaiosRecord> rows = await AutoLabFetch.BoundedListAsync(
            AutoLabQueries.RowsOfEquipmentKeys(
                _database, authorized, [.. keys.Items], _limits.MaxRowsPerQuery + 1),
            _limits.MaxRowsPerQuery,
            Unavailable,
            cancellationToken);

        HashSet<string> onThePage = new(keys.Items.Select(key => key.Trim()), StringComparer.Ordinal);

        return keys.WithItems<SampleAnalysis>(
            [.. AnalysesIn(rows, authorized)
                .Where(analysis => onThePage.Contains(analysis.EquipmentKey))]);
    }

    public async Task<Page<SampleAnalysis>> GetAnalysesOfEquipmentAsync(
        CompanyScope scope,
        string equipmentKey,
        PageRequest reportPage,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(scope);
        ArgumentNullException.ThrowIfNull(reportPage);

        if (string.IsNullOrWhiteSpace(equipmentKey)) return Page<SampleAnalysis>.Empty(reportPage);

        List<string> authorized = await AuthorizedProtocolsAsync(scope, cancellationToken);

        if (authorized.Count == 0) return Page<SampleAnalysis>.Empty(reportPage);

        string wantedKey = equipmentKey.Trim();

        Page<string> protocols = await AutoLabFetch.GuardAsync(
            () => AutoLabQueries.ProtocolsOfEquipment(_database, authorized, wantedKey)
                .ToPageAsync(reportPage, cancellationToken),
            Unavailable);

        if (protocols.Items.Count == 0) return protocols.WithItems<SampleAnalysis>([]);

        HashSet<string> onThePage = new(protocols.Items.Select(protocol => protocol.Trim()), StringComparer.Ordinal);
        List<string> authorizedOnThePage = [.. authorized.Where(protocol => onThePage.Contains(protocol.Trim()))];

        if (authorizedOnThePage.Count == 0) return protocols.WithItems<SampleAnalysis>([]);

        List<VwDadosEnsaiosRecord> rows = await AutoLabFetch.BoundedListAsync(
            AutoLabQueries.RowsOfProtocols(
                _database, authorizedOnThePage, [.. protocols.Items], _limits.MaxRowsPerQuery + 1),
            _limits.MaxRowsPerQuery,
            Unavailable,
            cancellationToken);

        return protocols.WithItems<SampleAnalysis>(
            [.. AnalysesIn(rows, authorizedOnThePage)
                .Where(analysis => string.Equals(analysis.EquipmentKey, wantedKey, StringComparison.Ordinal))
                .OrderByDescending(analysis => analysis.AnalysisEndDate)
                .ThenBy(analysis => analysis.ProtocoloLactec, StringComparer.Ordinal)]);
    }

    public async Task<SampleAnalysis?> GetAnalysisByProtocolAsync(
        CompanyScope scope,
        string protocoloLactec,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(scope);

        if (string.IsNullOrWhiteSpace(protocoloLactec)) return null;

        string wantedProtocol = protocoloLactec.Trim();

        List<string> authorized = [.. (await AuthorizedProtocolsAsync(scope, cancellationToken))
            .Where(protocol => string.Equals(protocol.Trim(), wantedProtocol, StringComparison.Ordinal))];

        if (authorized.Count == 0) return null;

        List<VwDadosEnsaiosRecord> rows = await AutoLabFetch.BoundedListAsync(
            AutoLabQueries.RowsOfProtocol(
                _database, authorized, wantedProtocol, _limits.MaxRowsPerQuery + 1),
            _limits.MaxRowsPerQuery,
            Unavailable,
            cancellationToken);

        return AnalysesIn(rows, authorized)
            .FirstOrDefault(analysis => string.Equals(
                analysis.ProtocoloLactec, wantedProtocol, StringComparison.Ordinal));
    }

    public async Task<IReadOnlyList<ProtocolReport>> GetReportsOfProtocolsAsync(
        CompanyScope scope,
        IReadOnlyCollection<string> protocols,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(scope);
        ArgumentNullException.ThrowIfNull(protocols);

        HashSet<string> wanted = new(
            protocols
                .Where(protocol => !string.IsNullOrWhiteSpace(protocol))
                .Select(protocol => protocol.Trim()),
            StringComparer.Ordinal);

        if (wanted.Count == 0) return [];

        List<string> authorized = [.. (await AuthorizedProtocolsAsync(scope, cancellationToken))
            .Where(protocol => wanted.Contains(protocol.Trim()))];

        if (authorized.Count == 0) return [];

        List<ProtocolReportRow> rows = await AutoLabFetch.BoundedListInChunksAsync(
            authorized,
            _limits,
            (chunk, rowLimit) => AutoLabQueries.ReportsOfProtocols(_database, chunk, rowLimit),
            Unavailable,
            cancellationToken);

        return [.. rows
            .Where(row => row.ProtocoloLactec is string protocol && wanted.Contains(protocol.Trim()))
            .GroupBy(row => row.ProtocoloLactec!.Trim(), StringComparer.Ordinal)
            .OrderBy(rowsOfProtocol => rowsOfProtocol.Key, StringComparer.Ordinal)
            .SelectMany(rowsOfProtocol => ProtocolHeader
                .Of(
                    rowsOfProtocol.Key,
                    rowsOfProtocol.Select(row => new ReportRow(
                        row.Identificacao, null, null, row.DataEmissao, null, HasMeasurements: true)))
                .Reports)];
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
        IReadOnlyList<Measurement> measurements = MeasurementsOf(rowsOfProtocol);

        ProtocolHeader header = ProtocolHeader.Of(
            rowsOfProtocol.Key,
            rowsOfProtocol
                .GroupBy(row => row.Identificacao?.Trim() ?? string.Empty, StringComparer.OrdinalIgnoreCase)
                .SelectMany(rowsOfReport =>
                {
                    bool hasMeasurements = MeasurementsOf(rowsOfReport).Count > 0;

                    return rowsOfReport.Select(row => new ReportRow(
                        row.Identificacao,
                        row.NumSerie,
                        row.EmpresaTrafo,
                        row.DataEmissao,
                        row.DataFinalAnalise,
                        hasMeasurements));
                }));

        return new SampleAnalysis(
            rowsOfProtocol.Key,
            header.Identification,
            header.SerialNumber,
            header.Company,
            header.IssueDate,
            header.AnalysisEndDate,
            measurements);
    }

    private static IReadOnlyList<Measurement> MeasurementsOf(IEnumerable<VwDadosEnsaiosRecord> rows)
    {
        return MeasuredItemRecognition.Identify(
            rows.Select(row => new ResultBeforeRecognition(
                row.Name ?? string.Empty,
                MeasuredValue.InterpretResult(row.Resultado))));
    }

    /// <summary>
    /// A linha que voltou do banco e conferida de novo contra os protocolos autorizados, com
    /// comparacao ordinal. O <c>IN</c> do banco segue a collation da coluna; esta conferencia
    /// nao, e linha que nao passa por ela nao vira analise.
    /// </summary>
    private static IEnumerable<SampleAnalysis> AnalysesIn(
        IEnumerable<VwDadosEnsaiosRecord> rows,
        IEnumerable<string> authorizedProtocols)
    {
        HashSet<string> authorized = new(
            authorizedProtocols.Select(protocol => protocol.Trim()), StringComparer.Ordinal);

        return BuildAnalyses(rows.Where(row => row.ProtocoloLactec is string protocol
            && authorized.Contains(protocol.Trim())));
    }

    private async Task<List<string>> AuthorizedProtocolsAsync(
        CompanyScope scope,
        CancellationToken cancellationToken)
    {
        try
        {
            return [.. await _companyRecords.GetProtocolsAsync(scope, cancellationToken)];
        }
        catch (CompanyRecordsSourceUnavailableException exception)
        {
            throw Unavailable(exception.Message, exception);
        }
    }

    private static LabTestSourceUnavailableException Unavailable(string message, Exception cause)
    {
        return new LabTestSourceUnavailableException(message, cause);
    }
}
