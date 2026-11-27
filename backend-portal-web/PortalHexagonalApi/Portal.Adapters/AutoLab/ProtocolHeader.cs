using Portal.Application.LabResults;

namespace Portal.Adapters.AutoLab;

internal sealed record ReportRow(
    string? Identificacao,
    string? NumSerie,
    string? EmpresaTrafo,
    DateTime? DataEmissao,
    DateTime? DataFinalAnalise,
    bool HasMeasurements);

internal sealed record ProtocolHeader(
    string Identification,
    string? SerialNumber,
    string Company,
    DateTime IssueDate,
    DateTime AnalysisEndDate,
    IReadOnlyList<ProtocolReport> Reports)
{
    public static ProtocolHeader Of(string protocol, IEnumerable<ReportRow> rowsOfProtocol)
    {
        List<ReportRow> rows = [.. rowsOfProtocol];

        var reports = rows
            .Where(row => !string.IsNullOrWhiteSpace(row.Identificacao))
            .GroupBy(row => row.Identificacao!.Trim(), StringComparer.OrdinalIgnoreCase)
            .Select(ofReport => new
            {
                Identification = ofReport.Select(row => row.Identificacao!.Trim()).Min(StringComparer.Ordinal)!,
                IssueDate = ofReport.Max(row => row.DataEmissao) ?? default,
                AnalysisEndDate = ofReport.Max(row => row.DataFinalAnalise) ?? default,
                HasMeasurements = ofReport.Any(row => row.HasMeasurements)
            })
            .OrderByDescending(report => report.HasMeasurements)
            .ThenByDescending(report => report.IssueDate)
            .ThenBy(report => report.Identification, StringComparer.Ordinal)
            .ToList();

        return new ProtocolHeader(
            reports.Count == 0 ? string.Empty : reports[0].Identification,
            Stated(rows.Select(row => row.NumSerie)),
            Stated(rows.Select(row => row.EmpresaTrafo)) ?? string.Empty,
            reports.Count == 0 ? default : reports[0].IssueDate,
            reports.Count == 0 ? default : reports[0].AnalysisEndDate,
            [.. reports.Select(report => new ProtocolReport(protocol, report.Identification, report.IssueDate))]);
    }

    private static string? Stated(IEnumerable<string?> values)
    {
        return values
            .Where(value => !string.IsNullOrWhiteSpace(value))
            .Select(value => value!.Trim())
            .Min(StringComparer.Ordinal);
    }
}
