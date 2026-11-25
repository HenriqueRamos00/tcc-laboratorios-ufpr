namespace Portal.Application.LabResults;

public sealed record ReportConclusion(
    string Identification,
    DateTime Date,
    string? ResultLabel,
    string? Title,
    IReadOnlyList<string> Paragraphs);
