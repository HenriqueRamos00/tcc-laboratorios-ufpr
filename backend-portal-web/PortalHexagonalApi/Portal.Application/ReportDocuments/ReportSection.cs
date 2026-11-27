namespace Portal.Application.ReportDocuments;

public sealed record ReportSection(
    string? ResultLabel,
    Conformity? Conformity,
    string? Title,
    IReadOnlyList<string> Paragraphs)
{
    public string? ResultColor { get; init; }

    public bool Equals(ReportSection? other)
    {
        return other is not null
            && ResultLabel == other.ResultLabel
            && Conformity == other.Conformity
            && Title == other.Title
            && ResultColor == other.ResultColor
            && Paragraphs.SequenceEqual(other.Paragraphs, StringComparer.Ordinal);
    }

    public override int GetHashCode()
    {
        return HashCode.Combine(ResultLabel, Conformity, Title, ResultColor, Paragraphs.Count);
    }
}
