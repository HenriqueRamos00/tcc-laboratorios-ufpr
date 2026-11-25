namespace Portal.Application.ReportDocuments;

public sealed record ReportSection(string? ResultLabel, string? Title, IReadOnlyList<string> Paragraphs);
