namespace Portal.Domain.Reports;

/// <summary>Documento técnico publicado e acessível ao cliente.</summary>
public sealed record PublishedReport(
    string Id,
    string Code,
    string Protocol,
    string Sample,
    string Laboratory,
    DateTimeOffset PublishedOn,
    DateTimeOffset? PublishedAt = null);

/// <summary>Bytes de um relatório publicado, sem regeneração na consulta.</summary>
public sealed record PublishedReportFile(byte[] Content);

/// <summary>Relatórios publicados de um orçamento na fonte consultada.</summary>
public sealed record QuotePublishedReports(
    string Protocol,
    string TestType,
    string Summary,
    IReadOnlyList<PublishedReport> Reports);
