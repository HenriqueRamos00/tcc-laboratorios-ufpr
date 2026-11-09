using Portal.Adapters.AutoLab;
using Portal.Application.Paging;
using Portal.Application.ReportDocuments;
using Portal.Application.ReportDocuments.Exceptions;
using Portal.Application.Visibility;

namespace Portal.Adapters.ReportDocuments;

public sealed class AutoLabReportIssueRepository : IReportIssueRepository
{
    private readonly AutoLabDbContext _database;
    private readonly AutoLabQueryLimits _limits;

    public AutoLabReportIssueRepository(AutoLabDbContext database, AutoLabQueryLimits limits)
    {
        _database = database;
        _limits = limits;
    }

    public async Task<SignedReportDocument?> GetSignedDocumentOfAsync(
        string reportIdentification,
        CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(reportIdentification)) return null;

        List<SignedDocumentRow> found = await AutoLabFetch.ListAsync(
            AutoLabQueries.SignedDocumentOf(_database, reportIdentification.Trim()),
            Unavailable,
            cancellationToken);

        return DocumentOf(found);
    }

    public async Task<IReadOnlySet<string>> GetIdentificationsWithSignedDocumentAsync(
        IReadOnlyCollection<string> reportIdentifications,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(reportIdentifications);

        string[] wanted = [.. reportIdentifications
            .Where(identification => !string.IsNullOrWhiteSpace(identification))
            .Select(identification => identification.Trim())
            .Distinct(StringComparer.Ordinal)];

        if (wanted.Length == 0) return new HashSet<string>(StringComparer.Ordinal);

        List<string> found = await AutoLabFetch.BoundedListInChunksAsync(
            wanted,
            _limits,
            (chunk, rowLimit) => AutoLabQueries.IdentificationsWithSignedDocument(_database, chunk, rowLimit),
            Unavailable,
            cancellationToken);

        HashSet<string> signed = new(found, StringComparer.OrdinalIgnoreCase);

        return new HashSet<string>(wanted.Where(signed.Contains), StringComparer.Ordinal);
    }

    public async Task<ReportIssue?> GetCurrentIssueOfAsync(
        string reportIdentification,
        CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(reportIdentification)) return null;

        List<ReportIssueRow> found = await AutoLabFetch.ListAsync(
            AutoLabQueries.CurrentIssueOf(_database, reportIdentification.Trim()),
            Unavailable,
            cancellationToken);

        ReportIssueRow? issue = found.FirstOrDefault();

        return issue is null
            ? null
            : new ReportIssue(Stated(issue.TituloRelatorio), Stated(issue.Conclusao));
    }

    public async Task<Page<LabReport>> ListReportsAsync(
        VisibilityScope scope,
        string? quoteCode,
        PageRequest page,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(scope);
        ArgumentNullException.ThrowIfNull(page);

        Page<VwRelatoriosEnsaiosRecord> reports = await AutoLabFetch.GuardAsync(
            () => AutoLabQueries.Reports(_database, scope, quoteCode).ToPageAsync(page, cancellationToken),
            Unavailable);

        return reports.WithItems<LabReport>([.. reports.Items.Select(report => new LabReport(
            report.Oid,
            Stated(report.Identificacao),
            Stated(report.NumeroSolicitacao),
            Stated(report.OrcamentoCRM),
            Stated(report.IDAmostra),
            Stated(report.Nome),
            Stated(report.ClienteSolicitante),
            Stated(report.SituacaoRelatorio),
            report.DataFinalProgramadaCliente,
            report.DataEmissao,
            report.DataPublicacao,
            report.PdfAssinado is not null))]);
    }

    public async Task<SignedReportDocument?> GetSignedDocumentOfReportAsync(
        VisibilityScope scope,
        Guid reportId,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(scope);

        List<SignedDocumentRow> found = await AutoLabFetch.ListAsync(
            AutoLabQueries.SignedDocumentOfReport(_database, scope, reportId),
            Unavailable,
            cancellationToken);

        return DocumentOf(found);
    }

    private static SignedReportDocument? DocumentOf(List<SignedDocumentRow> found)
    {
        SignedDocumentRow? document = found.FirstOrDefault();

        return document?.Content is { Length: > 0 } content
            ? new SignedReportDocument(document.FileName, content)
            : null;
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
