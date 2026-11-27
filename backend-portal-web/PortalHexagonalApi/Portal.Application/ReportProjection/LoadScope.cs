namespace Portal.Application.ReportProjection;

public sealed record LoadScope(
    string Cnpj,
    bool Active,
    DateTime? ProcessedUntilIssueDate,
    Guid? ProcessedUntilReportId,
    DateTime? LastRunAt);
