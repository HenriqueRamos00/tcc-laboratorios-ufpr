using System.Globalization;

using Portal.Application.Reports;
using Portal.Domain.Reports;

namespace Portal.Api.Reports;

public sealed record PublishedReportResponse(
    string Id,
    string Code,
    string Protocol,
    string Sample,
    string Laboratory,
    string PublishedOn)
{
    private static readonly TimeZoneInfo PublicationTimeZone = TimeZoneInfo.FindSystemTimeZoneById("America/Sao_Paulo");

    public static PublishedReportResponse From(PublishedReport report) => new(
        report.Id,
        report.Code,
        report.Protocol,
        report.Sample,
        report.Laboratory,
        PublicationDateOf(report).ToString("dd/MM/yyyy", CultureInfo.InvariantCulture));

    public static DateTimeOffset PublicationDateOf(PublishedReport report) =>
        report.PublishedAt is { } publishedAt
            ? TimeZoneInfo.ConvertTime(publishedAt, PublicationTimeZone)
            : report.PublishedOn;
}

public sealed record QuoteReportConclusionResponse(
    string QuoteId,
    string QuoteCode,
    string Protocol,
    string TestType,
    string Summary,
    IReadOnlyList<ReportResponse> Reports)
{
    public static QuoteReportConclusionResponse From(QuoteReportConclusion conclusion) => new(
        conclusion.QuoteId,
        conclusion.QuoteCode,
        conclusion.Protocol,
        conclusion.TestType,
        conclusion.Summary,
        conclusion.Reports.Select(report => ReportResponse.From(report) with
        {
            QuoteCode = conclusion.QuoteCode
        }).ToArray());
}
