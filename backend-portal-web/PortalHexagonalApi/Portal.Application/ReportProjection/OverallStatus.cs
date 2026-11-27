using Portal.Application.ReportDocuments;

namespace Portal.Application.ReportProjection;

public enum OverallStatus
{
    NonConforming,
    Warning,
    Normal,
    Unclassified,
    NoReport
}

public static class OverallStatusText
{
    public const string Unclassified = "sem-classificacao";
    public const string NoReport = "sem-laudo";

    public static string Of(OverallStatus status)
    {
        return status switch
        {
            OverallStatus.NonConforming => ConformityText.NonConforming,
            OverallStatus.Warning => ConformityText.Warning,
            OverallStatus.Normal => ConformityText.Normal,
            OverallStatus.Unclassified => Unclassified,
            _ => NoReport
        };
    }

    public static bool TryParse(string? text, out OverallStatus status)
    {
        (bool known, OverallStatus parsed) = text switch
        {
            ConformityText.NonConforming => (true, OverallStatus.NonConforming),
            ConformityText.Warning => (true, OverallStatus.Warning),
            ConformityText.Normal => (true, OverallStatus.Normal),
            Unclassified => (true, OverallStatus.Unclassified),
            NoReport => (true, OverallStatus.NoReport),
            _ => (false, OverallStatus.NoReport)
        };

        status = parsed;
        return known;
    }

    public static OverallStatus OfConformity(Conformity? worst)
    {
        return worst switch
        {
            Conformity.NonConforming => OverallStatus.NonConforming,
            Conformity.Warning => OverallStatus.Warning,
            Conformity.Normal => OverallStatus.Normal,
            _ => OverallStatus.Unclassified
        };
    }
}
