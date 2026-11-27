namespace Portal.Application.ReportDocuments;

public enum ReportReadOutcome
{
    Extracted,
    NoSections,
    Unreadable
}

public static class ReportReadOutcomeText
{
    public const string Extracted = "extraido";
    public const string NoSections = "sem-secoes";
    public const string Unreadable = "ilegivel";

    public static string Of(ReportReadOutcome outcome)
    {
        return outcome switch
        {
            ReportReadOutcome.Extracted => Extracted,
            ReportReadOutcome.NoSections => NoSections,
            _ => Unreadable
        };
    }

    public static ReportReadOutcome Parse(string text)
    {
        return text switch
        {
            Extracted => ReportReadOutcome.Extracted,
            NoSections => ReportReadOutcome.NoSections,
            _ => ReportReadOutcome.Unreadable
        };
    }
}
