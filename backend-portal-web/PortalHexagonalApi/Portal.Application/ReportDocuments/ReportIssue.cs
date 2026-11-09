using System.Text.RegularExpressions;

namespace Portal.Application.ReportDocuments;

public sealed partial record ReportIssue(string? Title, string? Conclusion)
{
    public IReadOnlyList<string> Paragraphs => string.IsNullOrWhiteSpace(Conclusion)
        ? []
        : [.. BlankLine().Split(Conclusion)
            .Select(paragraph => paragraph.Trim())
            .Where(paragraph => paragraph.Length > 0)];

    [GeneratedRegex(@"\r?\n[ \t]*\r?\n")]
    private static partial Regex BlankLine();
}
