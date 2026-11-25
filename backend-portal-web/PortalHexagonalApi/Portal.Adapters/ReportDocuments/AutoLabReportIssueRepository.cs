using Portal.Adapters.AutoLab;
using Portal.Application.CompanyRecords;
using Portal.Application.CompanyRecords.Exceptions;
using Portal.Application.Paging;
using Portal.Application.ReportDocuments;
using Portal.Application.ReportDocuments.Exceptions;
using Portal.Application.Visibility;

namespace Portal.Adapters.ReportDocuments;

/// <summary>
/// Relatorios e arquivos assinados do AutoLAB. Toda operacao parte das emissoes que
/// <see cref="ICompanyRecordsSource"/> entregou para o escopo: a identificacao e o
/// identificador que chegam de fora so escolhem entre elas, e um arquivo so e lido pelo
/// ponteiro de uma emissao autorizada.
///
/// <c>vwRelatoriosEnsaios</c> entra apenas para as colunas baratas, uma emissao por vez pelo
/// <c>Oid</c>; ela nunca e filtrada por lista. Emissao que a view nao mostra nao e servida.
/// </summary>
public sealed class AutoLabReportIssueRepository : IReportIssueRepository
{
    private readonly AutoLabDbContext _database;
    private readonly ICompanyRecordsSource _companyRecords;
    private readonly AutoLabQueryLimits _limits;

    public AutoLabReportIssueRepository(
        AutoLabDbContext database,
        ICompanyRecordsSource companyRecords,
        AutoLabQueryLimits limits)
    {
        _database = database;
        _companyRecords = companyRecords;
        _limits = limits;
    }

    public async Task<SignedReportDocument?> GetSignedDocumentOfAsync(
        CompanyScope scope,
        string reportIdentification,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(scope);

        if (string.IsNullOrWhiteSpace(reportIdentification)) return null;

        List<CompanyReportRow> signed = [.. IssuesOf(
                await AuthorizedReportsAsync(scope, cancellationToken), reportIdentification.Trim())
            .Where(report => report.SignedFileId is not null)];

        if (signed.Count == 0) return null;

        HashSet<Guid> live = await LiveFilesAmongAsync(signed, cancellationToken);

        CompanyReportRow? current = InOrderOfIssue(signed)
            .FirstOrDefault(report => live.Contains(report.SignedFileId!.Value));

        return current is null
            ? null
            : await DocumentOfAsync(current.SignedFileId!.Value, cancellationToken);
    }

    public async Task<IReadOnlySet<string>> GetIdentificationsWithSignedDocumentAsync(
        CompanyScope scope,
        IReadOnlyCollection<string> reportIdentifications,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(scope);
        ArgumentNullException.ThrowIfNull(reportIdentifications);

        string[] wanted = [.. reportIdentifications
            .Where(identification => !string.IsNullOrWhiteSpace(identification))
            .Select(identification => identification.Trim())
            .Distinct(StringComparer.Ordinal)];

        if (wanted.Length == 0) return new HashSet<string>(StringComparer.Ordinal);

        HashSet<string> asked = new(wanted, StringComparer.OrdinalIgnoreCase);

        List<CompanyReportRow> candidates = [.. (await AuthorizedReportsAsync(scope, cancellationToken))
            .Where(report => report.SignedFileId is not null
                && report.Identification is not null
                && asked.Contains(report.Identification.Trim()))];

        if (candidates.Count == 0) return new HashSet<string>(StringComparer.Ordinal);

        HashSet<Guid> live = await LiveFilesAmongAsync(candidates, cancellationToken);

        HashSet<string> signed = new(
            candidates
                .Where(report => live.Contains(report.SignedFileId!.Value))
                .Select(report => report.Identification!.Trim()),
            StringComparer.OrdinalIgnoreCase);

        return new HashSet<string>(wanted.Where(signed.Contains), StringComparer.Ordinal);
    }

    public async Task<ReportIssue?> GetCurrentIssueOfAsync(
        CompanyScope scope,
        string reportIdentification,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(scope);

        if (string.IsNullOrWhiteSpace(reportIdentification)) return null;

        CompanyReportRow? current = InOrderOfIssue(IssuesOf(
                await AuthorizedReportsAsync(scope, cancellationToken), reportIdentification.Trim()))
            .FirstOrDefault();

        if (current is null) return null;

        List<ReportIssueRow> found = await AutoLabFetch.ListAsync(
            AutoLabQueries.IssueTextOf(_database, current.Id),
            Unavailable,
            cancellationToken);

        ReportIssueRow? issue = found.FirstOrDefault();

        return issue is null
            ? null
            : new ReportIssue(Stated(issue.TituloRelatorio), Stated(issue.Conclusao));
    }

    public async Task<Page<LabReport>> ListReportsAsync(
        CompanyScope scope,
        string? quoteCode,
        PageRequest page,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(scope);
        ArgumentNullException.ThrowIfNull(page);

        IEnumerable<CompanyReportRow> authorized = await AuthorizedReportsAsync(scope, cancellationToken);

        if (quoteCode is not null)
            authorized = authorized.Where(report => report.QuoteCode is not null
                && string.Equals(report.QuoteCode.Trim(), quoteCode.Trim(), StringComparison.OrdinalIgnoreCase));

        Page<CompanyReportRow> reports = Page<CompanyReportRow>.Of(
            authorized
                .OrderByDescending(report => report.PublicationDate)
                .ThenByDescending(report => report.IssueDate)
                .ThenBy(report => report.Id),
            page);

        List<LabReport> listed = [];

        foreach (CompanyReportRow report in reports.Items)
        {
            ReportViewRow? shown = await ViewRowOfAsync(report.Id, cancellationToken);

            if (shown is null) continue;

            listed.Add(new LabReport(
                report.Id,
                Stated(report.Identification),
                Stated(report.RequestNumber),
                Stated(report.QuoteCode),
                Stated(shown.IDAmostra),
                Stated(shown.Nome),
                Stated(shown.ClienteSolicitante),
                Stated(shown.SituacaoRelatorio),
                shown.DataFinalProgramadaCliente,
                report.IssueDate,
                report.PublicationDate,
                report.SignedFileId is not null));
        }

        return reports.WithItems<LabReport>(listed);
    }

    public async Task<SignedReportDocument?> GetSignedDocumentOfReportAsync(
        CompanyScope scope,
        Guid reportId,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(scope);

        CompanyReportRow? report = (await AuthorizedReportsAsync(scope, cancellationToken))
            .FirstOrDefault(candidate => candidate.Id == reportId);

        if (report?.SignedFileId is not Guid fileId) return null;

        if (await ViewRowOfAsync(report.Id, cancellationToken) is null) return null;

        return await DocumentOfAsync(fileId, cancellationToken);
    }

    private static IEnumerable<CompanyReportRow> IssuesOf(
        IEnumerable<CompanyReportRow> authorized,
        string identification)
    {
        return authorized.Where(report => report.Identification is not null
            && string.Equals(report.Identification.Trim(), identification, StringComparison.OrdinalIgnoreCase));
    }

    private static IOrderedEnumerable<CompanyReportRow> InOrderOfIssue(IEnumerable<CompanyReportRow> issues)
    {
        return issues
            .OrderBy(report => report.IsReplaced)
            .ThenByDescending(report => report.Revision)
            .ThenByDescending(report => report.PublicationDate)
            .ThenByDescending(report => report.IssueDate)
            .ThenBy(report => report.Id);
    }

    private async Task<IReadOnlyList<CompanyReportRow>> AuthorizedReportsAsync(
        CompanyScope scope,
        CancellationToken cancellationToken)
    {
        try
        {
            return await _companyRecords.GetReportsAsync(scope, cancellationToken);
        }
        catch (CompanyRecordsSourceUnavailableException exception)
        {
            throw Unavailable(exception.Message, exception);
        }
    }

    private async Task<HashSet<Guid>> LiveFilesAmongAsync(
        IEnumerable<CompanyReportRow> signed,
        CancellationToken cancellationToken)
    {
        Guid[] fileIds = [.. signed.Select(report => report.SignedFileId!.Value).Distinct()];

        return [.. await AutoLabFetch.BoundedListInChunksAsync(
            fileIds,
            _limits,
            (chunk, rowLimit) => AutoLabQueries.LiveFilesAmong(_database, chunk, rowLimit),
            Unavailable,
            cancellationToken)];
    }

    private async Task<ReportViewRow?> ViewRowOfAsync(Guid reportId, CancellationToken cancellationToken)
    {
        List<ReportViewRow> found = await AutoLabFetch.ListAsync(
            AutoLabQueries.ReportViewRowOf(_database, reportId),
            Unavailable,
            cancellationToken);

        return found.FirstOrDefault();
    }

    private async Task<SignedReportDocument?> DocumentOfAsync(Guid fileId, CancellationToken cancellationToken)
    {
        List<SignedDocumentRow> found = await AutoLabFetch.ListAsync(
            AutoLabQueries.LiveFileOf(_database, fileId),
            Unavailable,
            cancellationToken);

        SignedDocumentRow? document = found.FirstOrDefault();

        if (document?.Content is not { Length: > 0 } stored) return null;

        try
        {
            return new SignedReportDocument(document.FileName, FileDataContent.Decode(stored));
        }
        catch (InvalidDataException exception)
        {
            throw Unavailable("O arquivo assinado do AutoLAB não pôde ser lido.", exception);
        }
    }

    private static string? Stated(string? value)
    {
        return string.IsNullOrWhiteSpace(value) ? null : value.Trim();
    }

    private static ReportDocumentSourceUnavailableException Unavailable(string message, Exception cause)
    {
        return new ReportDocumentSourceUnavailableException(message, cause);
    }
}
