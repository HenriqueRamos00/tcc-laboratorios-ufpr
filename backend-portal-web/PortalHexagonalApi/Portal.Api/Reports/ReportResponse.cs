using System.Globalization;

using Portal.Api.LabResults;
using Portal.Application.ReportDocuments;
using Portal.Domain.Reports;

namespace Portal.Api.Reports;

public sealed record ReportResponse
{
    public required string Id { get; init; }

    public string? Identification { get; init; }

    public string? RequestNumber { get; init; }

    public string? QuoteCode { get; init; }

    public string? SampleId { get; init; }

    public string? SampleName { get; init; }

    public string? Client { get; init; }

    public string? Status { get; init; }

    public string? ScheduledDate { get; init; }

    public string? IssueDate { get; init; }

    public string? PublicationDate { get; init; }

    public required bool HasSignedDocument { get; init; }

    public bool PdfAvailable { get; init; }

    // Preserve the published-report fields consumed by existing clients of the Spring bridge.
    public string? Code { get; init; }
    public string? Protocol { get; init; }
    public string? Sample { get; init; }
    public string? Laboratory { get; init; }
    public string? PublishedOn { get; init; }

    public static ReportResponse From(PublishedReport report)
    {
        PublishedReportResponse published = PublishedReportResponse.From(report);
        return new ReportResponse
        {
            Id = report.Id,
            Identification = report.Code,
            RequestNumber = report.Protocol,
            SampleName = report.Sample,
            Status = "Publicado",
            PublicationDate = PublishedReportResponse.PublicationDateOf(report)
                .ToString("yyyy-MM-dd", CultureInfo.InvariantCulture),
            HasSignedDocument = false,
            PdfAvailable = true,
            Code = published.Code,
            Protocol = published.Protocol,
            Sample = published.Sample,
            Laboratory = published.Laboratory,
            PublishedOn = published.PublishedOn
        };
    }

    public static ReportResponse From(LabReport report) => new()
    {
        Id = OpaqueIdentifier.Encode(report.Id.ToString("D")),
        Identification = report.Identification,
        RequestNumber = report.RequestNumber,
        QuoteCode = report.QuoteCode,
        SampleId = report.SampleId,
        SampleName = report.SampleName,
        Client = report.Client,
        Status = report.Status,
        ScheduledDate = DateOf(report.ScheduledDate),
        IssueDate = DateOf(report.IssueDate),
        PublicationDate = DateOf(report.PublicationDate),
        HasSignedDocument = report.HasSignedDocument,
        PdfAvailable = report.HasSignedDocument,
        Code = report.Identification,
        Protocol = report.RequestNumber,
        Sample = report.SampleName,
        PublishedOn = DateOf(report.PublicationDate)
    };

    private static string? DateOf(DateTime? moment) => moment is null ? null : WireDate.From(moment.Value);
}
