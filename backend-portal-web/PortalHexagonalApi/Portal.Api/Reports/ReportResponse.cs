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
}
