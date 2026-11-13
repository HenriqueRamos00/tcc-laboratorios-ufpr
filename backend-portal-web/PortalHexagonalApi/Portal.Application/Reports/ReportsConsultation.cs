using Portal.Application.Quotes;
using Portal.Domain.Reports;

namespace Portal.Application.Reports;

/// <summary>Coordena a consulta de relatórios com o orçamento que os originou.</summary>
public sealed class ReportsConsultation(
    IReportDocumentRepository reports,
    IQuoteRepository quotes) : IForConsultingReports
{
    public Task<IReadOnlyList<PublishedReport>> ListPublishedAsync(CancellationToken cancellationToken) =>
        reports.GetPublishedAsync(cancellationToken);

    public async Task<QuoteReportConclusion?> GetPublishedOfQuoteAsync(
        string quoteId,
        CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(quoteId)) return null;

        var quote = await quotes.GetQuoteByIdAsync(quoteId, cancellationToken);
        if (quote is null) return null;

        QuotePublishedReports result = await reports.GetPublishedOfQuoteAsync(quoteId, cancellationToken);

        return new QuoteReportConclusion(
            quote.Id,
            quote.Code,
            result.Protocol,
            result.TestType,
            result.Summary,
            result.Reports);
    }

    public Task<PublishedReportFile?> GetFileAsync(
        string reportId,
        CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(reportId)) return Task.FromResult<PublishedReportFile?>(null);
        return reports.GetFileAsync(reportId, cancellationToken);
    }
}
