namespace Portal.Application.ReportDocuments;

public sealed record ReportReading(
    string Identification,
    DateTime? IssueDate,
    ReportSection? Chromatography,
    ReportSection? PhysicalChemical)
{
    public SignedReportOrigin? Origin { get; init; }
}

public sealed record SignedReportOrigin(
    string? Cnpj,
    Guid ReportId,
    Guid SignedFileId,
    ReportReadOutcome Outcome);
