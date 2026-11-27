namespace Portal.Application.ReportDocuments;

public enum Conformity
{
    Normal,
    Warning,
    NonConforming
}

public static class ConformityText
{
    public const string Normal = "normal";
    public const string Warning = "alerta";
    public const string NonConforming = "nao-conforme";

    public static string? Of(Conformity? conformity)
    {
        return conformity switch
        {
            Conformity.Normal => Normal,
            Conformity.Warning => Warning,
            Conformity.NonConforming => NonConforming,
            _ => null
        };
    }

    public static Conformity? Parse(string? text)
    {
        return text switch
        {
            Normal => Conformity.Normal,
            Warning => Conformity.Warning,
            NonConforming => Conformity.NonConforming,
            _ => null
        };
    }

    public static Conformity? Worst(Conformity? first, Conformity? second)
    {
        if (first is null) return second;
        if (second is null) return first;

        return (Conformity)Math.Max((int)first, (int)second);
    }
}
