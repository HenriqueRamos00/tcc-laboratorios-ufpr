using Portal.Application.ReportDocuments;

namespace Portal.Application.LabResults;

public sealed record ReportConclusion(
    string Identification,
    DateTime Date,
    string? ResultLabel,
    Conformity? Conformity,
    string? Title,
    IReadOnlyList<string> Paragraphs);
