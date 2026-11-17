using System.Text.RegularExpressions;

namespace Portal.Application.Visibility;

/// <summary>
/// As empresas que a sessao autenticada alcanca, identificadas pelo CNPJ sem mascara. E o
/// unico criterio de visibilidade dos dados do laboratorio: nao existe escopo "todas as
/// empresas", e o nome da empresa nunca decide nada.
///
/// O escopo fecha na duvida. Lista vazia, nula ou com qualquer valor que nao tenha a forma de
/// CNPJ vira <see cref="None"/>, que nao enxerga registro nenhum; um valor malformado nao e
/// descartado em silencio para aproveitar os demais, porque um token com claim estranha nao
/// e um token em que se confia pela metade.
/// </summary>
public sealed partial class CompanyScope : IEquatable<CompanyScope>
{
    private readonly HashSet<string> _cnpjs;

    private CompanyScope(IEnumerable<string> cnpjs)
    {
        Cnpjs = [.. cnpjs.Distinct(StringComparer.Ordinal).Order(StringComparer.Ordinal)];
        _cnpjs = new HashSet<string>(Cnpjs, StringComparer.Ordinal);
    }

    public static CompanyScope None { get; } = new([]);

    public IReadOnlyList<string> Cnpjs { get; }

    public bool IsEmpty => Cnpjs.Count == 0;

    public static CompanyScope OfCnpjs(IEnumerable<string?>? cnpjs)
    {
        if (cnpjs is null) return None;

        List<string> accepted = [];

        foreach (string? cnpj in cnpjs)
        {
            if (cnpj is null || !CnpjFormat().IsMatch(cnpj)) return None;

            accepted.Add(cnpj);
        }

        return accepted.Count == 0 ? None : new CompanyScope(accepted);
    }

    /// <summary>
    /// O CNPJ como a fonte o guarda (<c>12.345.678/0001-90</c>) reduzido a forma do escopo.
    /// Devolve nulo quando o que sobra nao e um CNPJ: CPF, texto livre e campo vazio nao
    /// pertencem a empresa nenhuma.
    /// </summary>
    public static string? Normalize(string? storedCnpj)
    {
        if (storedCnpj is null) return null;

        string bare = storedCnpj
            .Replace(".", string.Empty, StringComparison.Ordinal)
            .Replace("/", string.Empty, StringComparison.Ordinal)
            .Replace("-", string.Empty, StringComparison.Ordinal)
            .Replace(" ", string.Empty, StringComparison.Ordinal);

        return CnpjFormat().IsMatch(bare) ? bare : null;
    }

    public bool Includes(string? storedCnpj)
    {
        return Normalize(storedCnpj) is string cnpj && _cnpjs.Contains(cnpj);
    }

    public bool Equals(CompanyScope? other)
    {
        return other is not null && Cnpjs.SequenceEqual(other.Cnpjs, StringComparer.Ordinal);
    }

    public override bool Equals(object? obj) => Equals(obj as CompanyScope);

    public override int GetHashCode()
    {
        HashCode hash = new();

        foreach (string cnpj in Cnpjs) hash.Add(cnpj, StringComparer.Ordinal);

        return hash.ToHashCode();
    }

    public override string ToString() => $"CompanyScope {{ Companies = {Cnpjs.Count} }}";

    [GeneratedRegex(@"\A[A-Z0-9]{12}[0-9]{2}\z", RegexOptions.CultureInvariant)]
    private static partial Regex CnpjFormat();
}
