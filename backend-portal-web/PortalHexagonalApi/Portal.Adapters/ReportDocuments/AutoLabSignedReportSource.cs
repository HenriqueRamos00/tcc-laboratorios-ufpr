using Portal.Adapters.AutoLab;
using Portal.Application.CompanyRecords;
using Portal.Application.CompanyRecords.Exceptions;
using Portal.Application.ReportDocuments;
using Portal.Application.ReportDocuments.Exceptions;
using Portal.Application.ReportProjection;
using Portal.Application.Visibility;

namespace Portal.Adapters.ReportDocuments;

public sealed class AutoLabSignedReportSource : ISignedReportSource
{
    private readonly AutoLabDbContext _database;
    private readonly ICompanyRecordsSource _companyRecords;
    private readonly AutoLabQueryLimits _limits;
    private readonly Dictionary<string, List<Guid?>> _clients = new(StringComparer.Ordinal);

    public AutoLabSignedReportSource(
        AutoLabDbContext database,
        ICompanyRecordsSource companyRecords,
        AutoLabQueryLimits limits)
    {
        _database = database;
        _companyRecords = companyRecords;
        _limits = limits;
    }

    public async Task<IReadOnlyList<SignedReportPointer>> NextSignedReportsAsync(
        string cnpj,
        DateTime? fromIssueDate,
        SignedReportCursor? after,
        int top,
        CancellationToken cancellationToken)
    {
        ArgumentOutOfRangeException.ThrowIfLessThan(top, 1);

        List<Guid?> clients = await ClientsOfAsync(cnpj, cancellationToken);

        if (clients.Count == 0) return [];

        List<SignedReportRow> rows = await AutoLabFetch.ListAsync(
            AutoLabQueries.SignedReportsOfClients(
                _database,
                clients,
                fromIssueDate,
                after?.IssueDate,
                after?.ReportId,
                Math.Min(top, _limits.MaxRowsPerQuery)),
            Unavailable,
            cancellationToken);

        return [.. rows.Select(row => new SignedReportPointer(
            row.ReportId,
            row.Identification?.Trim() ?? string.Empty,
            row.IssueDate,
            row.SignedFileId))];
    }

    public async Task<IReadOnlyList<CompanyProtocol>> GetProtocolsOfCompanyAsync(
        string cnpj,
        CancellationToken cancellationToken)
    {
        IReadOnlyCollection<string> authorized;

        try
        {
            authorized = await _companyRecords.GetProtocolsAsync(CompanyScope.OfCnpjs([cnpj]), cancellationToken);
        }
        catch (CompanyRecordsSourceUnavailableException exception)
        {
            throw Unavailable(exception.Message, exception);
        }

        HashSet<string> known = new(authorized.Select(protocol => protocol.Trim()), StringComparer.Ordinal);
        List<ProtocolSummaryRow> summaries = [];

        foreach (string[] chunk in authorized.Chunk(_limits.MaxValuesPerInClause))
        {
            cancellationToken.ThrowIfCancellationRequested();

            List<ProtocolSummaryRow> rows = await AutoLabFetch.BoundedListAsync(
                AutoLabQueries.ProtocolSummaries(_database, [.. chunk], _limits.MaxRowsPerQuery + 1),
                _limits.MaxRowsPerQuery,
                Unavailable,
                cancellationToken);

            summaries.AddRange(rows
                .Where(row => !string.IsNullOrWhiteSpace(row.ProtocoloLactec)
                    && known.Contains(row.ProtocoloLactec.Trim())
                    && !string.IsNullOrWhiteSpace(row.Identificacao)
                    && !string.IsNullOrWhiteSpace(row.EmpresaTrafo)));
        }

        return [.. summaries
            .GroupBy(row => row.ProtocoloLactec!.Trim(), StringComparer.Ordinal)
            .Select(rowsOfProtocol =>
            {
                ProtocolHeader header = ProtocolHeader.Of(
                    rowsOfProtocol.Key,
                    rowsOfProtocol.Select(row => new ReportRow(
                        row.Identificacao,
                        row.NumSerie,
                        row.EmpresaTrafo,
                        row.DataEmissao,
                        row.DataFinalAnalise,
                        HasMeasurements: true)));

                return new CompanyProtocol(
                    rowsOfProtocol.Key,
                    header.Identification,
                    header.SerialNumber,
                    header.Company,
                    header.IssueDate,
                    header.AnalysisEndDate,
                    header.Reports);
            })];
    }

    public async Task<SignedReportFindings> ReadAsync(Guid signedFileId, CancellationToken cancellationToken)
    {
        List<SignedDocumentRow> found = await AutoLabFetch.ListAsync(
            AutoLabQueries.LiveFileOf(_database, signedFileId),
            Unavailable,
            cancellationToken);

        if (found.FirstOrDefault()?.Content is not { Length: > 0 } stored)
            return SignedReportFindings.Unreadable;

        try
        {
            return SignedReportText.Read(FileDataContent.Decode(stored));
        }
        catch (InvalidDataException)
        {
            return SignedReportFindings.Unreadable;
        }
    }

    private async Task<List<Guid?>> ClientsOfAsync(string cnpj, CancellationToken cancellationToken)
    {
        if (_clients.TryGetValue(cnpj, out List<Guid?>? known)) return known;

        CompanyScope company = CompanyScope.OfCnpjs([cnpj]);

        List<ClienteRow> candidates = company.IsEmpty
            ? []
            : await AutoLabFetch.BoundedListAsync(
                AutoLabQueries.ClientsOfCnpjs(_database, [.. company.Cnpjs], _limits.MaxValuesPerInClause + 1),
                _limits.MaxValuesPerInClause,
                Unavailable,
                cancellationToken);

        List<Guid?> clients = [.. candidates
            .Where(candidate => company.Includes(candidate.CadastroNacional))
            .Select(candidate => (Guid?)candidate.Oid)
            .Distinct()];

        _clients[cnpj] = clients;

        return clients;
    }

    private static ReportDocumentSourceUnavailableException Unavailable(string message, Exception cause)
    {
        return new ReportDocumentSourceUnavailableException(message, cause);
    }
}
