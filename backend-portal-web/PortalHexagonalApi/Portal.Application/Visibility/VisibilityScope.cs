namespace Portal.Application.Visibility;

public sealed record VisibilityScope
{
    private VisibilityScope(string? company)
    {
        Company = company;
    }

    public static VisibilityScope All { get; } = new((string?)null);

    public string? Company { get; }

    public bool IsAll => Company is null;

    public static VisibilityScope OfCompany(string company)
    {
        if (string.IsNullOrWhiteSpace(company))
            throw new ArgumentException("Escopo de empresa sem empresa.", nameof(company));

        return new VisibilityScope(company.Trim());
    }

    public bool Includes(string? company)
    {
        return Company is null
            || string.Equals(company?.Trim(), Company, StringComparison.OrdinalIgnoreCase);
    }
}
