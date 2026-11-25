using Portal.Application.Paging;
using Portal.Application.Visibility;

namespace Portal.Application.ReportDocuments;

/// <summary>
/// Porta conduzida das emissoes de relatorio e dos arquivos assinados. Toda operacao recebe o
/// escopo de empresas: a identificacao de um relatorio e texto livre da fonte e pode repetir
/// entre clientes, entao ela sozinha nunca escolhe um arquivo.
/// </summary>
public interface IReportIssueRepository
{
    Task<SignedReportDocument?> GetSignedDocumentOfAsync(
        CompanyScope scope,
        string reportIdentification,
        CancellationToken cancellationToken);

    Task<IReadOnlySet<string>> GetIdentificationsWithSignedDocumentAsync(
        CompanyScope scope,
        IReadOnlyCollection<string> reportIdentifications,
        CancellationToken cancellationToken);

    Task<IReadOnlyList<ReportReading>> ReadReportsAsync(
        CompanyScope scope,
        IReadOnlyList<string> reportIdentifications,
        CancellationToken cancellationToken);

    Task<Page<LabReport>> ListReportsAsync(
        CompanyScope scope,
        string? quoteCode,
        PageRequest page,
        CancellationToken cancellationToken);

    Task<SignedReportDocument?> GetSignedDocumentOfReportAsync(
        CompanyScope scope,
        Guid reportId,
        CancellationToken cancellationToken);
}
