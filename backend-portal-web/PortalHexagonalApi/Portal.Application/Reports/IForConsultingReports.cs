using Portal.Domain.Reports;

namespace Portal.Application.Reports;

/// <summary>Operações documentais que o Portal do Cliente oferece ao usuário autenticado.</summary>
public interface IForConsultingReports
{
    Task<IReadOnlyList<PublishedReport>> ListPublishedAsync(CancellationToken cancellationToken);

    Task<QuoteReportConclusion?> GetPublishedOfQuoteAsync(
        string quoteId,
        CancellationToken cancellationToken);

    Task<PublishedReportFile?> GetFileAsync(string reportId, CancellationToken cancellationToken);
}

/// <summary>Visão da tela de relatórios de um orçamento autorizado.</summary>
public sealed record QuoteReportConclusion(
    string QuoteId,
    string QuoteCode,
    string Protocol,
    string TestType,
    string Summary,
    IReadOnlyList<PublishedReport> Reports);
