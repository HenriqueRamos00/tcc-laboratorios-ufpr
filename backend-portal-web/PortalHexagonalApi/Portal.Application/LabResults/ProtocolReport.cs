namespace Portal.Application.LabResults;

public sealed record ProtocolReport(string ProtocoloLactec, string Identification, DateTime IssueDate)
{
    public static IEnumerable<ProtocolReport> FromMostRecent(IEnumerable<ProtocolReport> reports)
    {
        ArgumentNullException.ThrowIfNull(reports);

        return reports
            .OrderByDescending(report => report.IssueDate)
            .ThenBy(report => report.Identification, StringComparer.Ordinal)
            .ThenBy(report => report.ProtocoloLactec, StringComparer.Ordinal);
    }
}
