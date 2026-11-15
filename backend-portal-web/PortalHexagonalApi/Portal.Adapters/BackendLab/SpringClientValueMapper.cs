namespace Portal.Adapters.BackendLab;

/// <summary>Normaliza códigos e enums do contrato Spring na borda do adapter.</summary>
internal static class SpringClientValueMapper
{
    private const string PartsPerMillion = "ppm";

    private static readonly IReadOnlyDictionary<(string Code, string Unit), string> IndicatorCodes =
        new Dictionary<(string Code, string Unit), string>
        {
            [("neutralization-index", "mg KOH/g")] = "neutralizacao",
            [("water-content", "ppm")] = "agua",
            [("density", "g/mL")] = "densidade",
            [("power-factor-100c", "%")] = "fatorDePotencia",
            [("dielectric-strength", "kV")] = "rigidez",
            [("interfacial-tension", "dina/cm")] = "tensaoInterfacial",
            [("combustible-gases", PartsPerMillion)] = "combustiveis",
            [("total-gases", PartsPerMillion)] = "total",
            [("h2", PartsPerMillion)] = "h2",
            [("o2", PartsPerMillion)] = "o2",
            [("n2", PartsPerMillion)] = "n2",
            [("ch4", PartsPerMillion)] = "ch4",
            [("co", PartsPerMillion)] = "co",
            [("co2", PartsPerMillion)] = "co2",
            [("c2h4", PartsPerMillion)] = "c2h4",
            [("c2h6", PartsPerMillion)] = "c2h6",
            [("c2h2", PartsPerMillion)] = "c2h2"
        };

    public static string? IndicatorCode(string code, string? unit) => unit is not null
        && IndicatorCodes.TryGetValue((code, unit), out string? mapped)
            ? mapped
            : null;

    public static string? DisplayEquipmentType(string? type)
    {
        if (string.IsNullOrWhiteSpace(type)) return null;

        return type.Trim() switch
        {
            "TRANSFORMADOR" => "Transformador",
            "DISJUNTOR" => "Disjuntor",
            "REATOR" => "Reator",
            "COMUTADOR" => "Comutador",
            "TANQUE" => "Tanque",
            string code when IsEnumCode(code) => HumanizeCode(code),
            _ => type.Trim()
        };
    }

    private static bool IsEnumCode(string value) =>
        value.Length > 0 && value.All(character => char.IsAsciiLetterUpper(character) || character == '_');

    private static string HumanizeCode(string value) => string.Join(
        ' ',
        value.Split('_', StringSplitOptions.RemoveEmptyEntries)
            .Select(part => string.Concat(char.ToUpperInvariant(part[0]), part[1..].ToLowerInvariant())));
}
