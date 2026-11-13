namespace Portal.Application.ReportDocuments;

public sealed record LabReport(
    Guid Id,
    string? Identification,
    string? RequestNumber,
    string? QuoteCode,
    string? SampleId,
    string? SampleName,
    string? Client,
    string? Status,
    DateTime? ScheduledDate,
    DateTime? IssueDate,
    DateTime? PublicationDate,
    bool HasSignedDocument);
