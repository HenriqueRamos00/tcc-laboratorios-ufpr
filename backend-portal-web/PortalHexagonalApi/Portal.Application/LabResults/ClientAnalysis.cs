namespace Portal.Application.LabResults;

public sealed record ClientCompany(string Id, string Name);

public sealed record ClientQuote(string Id, string? Protocol);

public sealed record ClientSample(string Id, string? Code, string? Name);

public sealed record ClientAnalysisEquipment(
    string Id,
    string? SerialNumber,
    string? Tag,
    string? Type);

public sealed record ClientAnalysisSummary(
    string Id,
    string ReportId,
    string TestId,
    string? TestCode,
    string ReportCode,
    string? TestType,
    string? AnalysisType,
    DateOnly? ScheduledAt,
    DateTimeOffset? ValidatedAt,
    DateTimeOffset? PublishedAt,
    string? TechnicalClassification,
    string? Conclusion,
    bool PdfAvailable);

public sealed record ClientMeasurement(
    string Code,
    string Name,
    string? Unit,
    string? Method,
    string? ReferenceLimit,
    ClientMeasurementResult Result,
    string? IndicatorCode = null);

public abstract record ClientMeasurementResult
{
    private ClientMeasurementResult() { }

    public sealed record Quantity(decimal Value) : ClientMeasurementResult;
    public sealed record NotDetected : ClientMeasurementResult;
    public sealed record NotPerformed : ClientMeasurementResult;
    public sealed record Text(string Value) : ClientMeasurementResult;
}

/// <summary>
/// Uma publicação liberada. Protocol é apenas dado de exibição; Id/ReportId identificam a
/// publicação e TestId identifica a execução que originou a medição.
/// </summary>
public sealed record ClientAnalysis(
    string Id,
    string ReportId,
    string TestId,
    string? TestCode,
    string ReportCode,
    ClientCompany Company,
    ClientQuote Quote,
    ClientSample Sample,
    ClientAnalysisEquipment? Equipment,
    string? TestType,
    string? AnalysisType,
    DateOnly? ScheduledAt,
    DateTimeOffset? ValidatedAt,
    DateTimeOffset PublishedAt,
    string? TechnicalClassification,
    string? Conclusion,
    bool PdfAvailable,
    IReadOnlyList<ClientMeasurement> Measurements);

public sealed record ClientReportDetail(ClientAnalysis Analysis, string FileUrl);
