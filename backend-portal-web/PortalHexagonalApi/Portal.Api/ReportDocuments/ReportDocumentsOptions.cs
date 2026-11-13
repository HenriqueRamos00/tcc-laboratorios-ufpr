namespace Portal.Api.ReportDocuments;

public sealed class ReportDocumentsOptions
{
    public const string SectionName = "ReportDocuments";

    public const string NotConnectedSource = "not-connected";
    public const string SqlServerSource = "sql-server";

    public string? Source { get; init; }
}
