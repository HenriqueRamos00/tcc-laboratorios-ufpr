namespace Portal.Application.LabResults;

public sealed record EquipmentSearch
{
    public const int MaxLength = 100;

    private EquipmentSearch(string term)
    {
        Term = term;
    }

    public string Term { get; }

    public static bool TryCreate(string? text, out EquipmentSearch? search)
    {
        search = null;

        if (string.IsNullOrWhiteSpace(text)) return true;

        string term = text.Trim();

        if (term.Length > MaxLength) return false;

        search = new EquipmentSearch(term);
        return true;
    }

    public override string ToString() => Term;
}
