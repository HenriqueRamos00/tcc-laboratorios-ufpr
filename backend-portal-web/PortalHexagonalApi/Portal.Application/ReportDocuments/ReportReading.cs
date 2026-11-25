namespace Portal.Application.ReportDocuments;

public sealed record ReportReading(
    string Identification,
    DateTime? IssueDate,
    ReportSection? Chromatography,
    ReportSection? PhysicalChemical);
