using Portal.Domain.Reports;

namespace Portal.Application.Reports;

/// <summary>Fonte de metadados e bytes de relatórios publicados.</summary>
public interface IReportDocumentRepository
{
    Task<IReadOnlyList<PublishedReport>> GetPublishedAsync(CancellationToken cancellationToken);

    Task<QuotePublishedReports> GetPublishedOfQuoteAsync(
        string quoteId,
        CancellationToken cancellationToken);

    Task<PublishedReportFile?> GetFileAsync(string reportId, CancellationToken cancellationToken);
}
