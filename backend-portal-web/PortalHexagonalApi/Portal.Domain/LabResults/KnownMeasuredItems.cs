using Portal.Domain.LabResults.Exceptions;

namespace Portal.Domain.LabResults;

/// <summary>
/// Lista de itens conhecidos, chaveada pela string exata da coluna <c>Name</c> da view. E a unica
/// fonte de formula, unidade, papel, metodo e limite normativo, porque nenhuma dessas
/// cinco informacoes existe no AutoLAB. Nome fora dessa lista falha em vez de virar um
/// item medido sem unidade: Duval e a IEEE C57.104 sao definidos em ppm, e publicar numero sem
/// unidade da lista deixaria a tela comparar ppm com miligrama por grama em silencio.
///
/// A marca de gas combustivel foi conferida contra a soma publicada de
/// "Total de Gases Combustiveis" nos dois protocolos de cromatografia da amostra real:
/// monoxido de carbono entra na soma; dioxido de carbono, oxigenio e nitrogenio nao.
/// </summary>
public static class KnownMeasuredItems
{
    public const string TotalGasesCode = "total";
    public const string CombustibleGasesTotalCode = "combustiveis";

    /// <summary>
    /// Marca de lacuna conhecida, para a lista de itens conhecidos poder listar um item
    /// medido que a grade do AutoLAB traz sem que o portal invente metodo, unidade ou limite
    /// para ele. A alternativa era recusar o item medido e derrubar o laudo inteiro, ou
    /// escrever um numero plausivel, que e pior: a tela compararia o resultado com um limite
    /// que norma nenhuma estabeleceu.
    /// </summary>
    public const string MethodNotCuratedYet = "-";

    public const string LimitNotCuratedYet = "-";

    private static readonly Dictionary<string, MeasuredItem> BySourceName = new(StringComparer.Ordinal)
    {
        ["Hidrogênio"] = Gas("h2", "Hidrogênio", "H₂", isCombustible: true),
        ["Oxigênio"] = Gas("o2", "Oxigênio", "O₂", isCombustible: false),
        ["Nitrogênio"] = Gas("n2", "Nitrogênio", "N₂", isCombustible: false),
        ["Metano"] = Gas("ch4", "Metano", "CH₄", isCombustible: true),
        ["Monóxido de Carbono"] = Gas("co", "Monóxido de Carbono", "CO", isCombustible: true),
        ["Dióxido de Carbono"] = Gas("co2", "Dióxido de Carbono", "CO₂", isCombustible: false),
        ["Etileno"] = Gas("c2h4", "Etileno", "C₂H₄", isCombustible: true),
        ["Etano"] = Gas("c2h6", "Etano", "C₂H₆", isCombustible: true),
        ["Acetileno"] = Gas("c2h2", "Acetileno", "C₂H₂", isCombustible: true),

        ["Total de Gases Combustíveis"] = Totalizer(
            CombustibleGasesTotalCode,
            "Total de Gases Combustíveis"),
        ["Total de Gases"] = Totalizer(TotalGasesCode, "Total de Gases"),

        ["Índice de Neutralização (mg KOH/g óleo)"] = PhysicalChemicalProperty(
            "neutralizacao",
            "Índice de Neutralização (mg KOH/g óleo)",
            "mg KOH/g óleo",
            "NBR 14248",
            "máx. 0,20"),
        ["Teor de Água (ppm m/m) - Medido"] = PhysicalChemicalProperty(
            "agua",
            "Teor de Água (ppm m/m) - Medido",
            "ppm m/m",
            "NBR 10710",
            "máx. 40"),
        ["Densidade a 20/4°C (g/mL)"] = PhysicalChemicalProperty(
            "densidade",
            "Densidade a 20/4°C (g/mL)",
            "g/mL",
            "NBR 14065",
            "-"),
        ["Fator de Potência a 100°C (%)"] = PhysicalChemicalProperty(
            "fatorDePotencia",
            "Fator de Potência a 100°C (%)",
            "%",
            "NBR 12133",
            "máx. 20"),
        ["Rigidez Dielétrica - Calota (kV)"] = PhysicalChemicalProperty(
            "rigidez",
            "Rigidez Dielétrica - Calota (kV)",
            "kV",
            "NBR/IEC 60156",
            "min. 40"),
        ["Tensão Interfacial (dina/cm)"] = PhysicalChemicalProperty(
            "tensaoInterfacial",
            "Tensão Interfacial (dina/cm)",
            "dina/cm",
            "NBR 6234",
            "min. 20"),

        ["Fator de Potência a 25°C (%)"] = PhysicalChemicalProperty(
            "fatorDePotencia25",
            "Fator de Potência a 25°C (%)",
            "%",
            MethodNotCuratedYet,
            LimitNotCuratedYet),
        ["Estabilidade à oxidação - ACIDEZ"] = PhysicalChemicalProperty(
            "estabilidadeAOxidacaoAcidez",
            "Estabilidade à oxidação - ACIDEZ",
            MethodNotCuratedYet,
            MethodNotCuratedYet,
            LimitNotCuratedYet)
    };

    public static IReadOnlyCollection<MeasuredItem> All => BySourceName.Values;

    /// <summary>
    /// Posicao do item medido na ordem em que a lista de itens conhecidos foi declarado, que
    /// e a ordem em que a tabela da tela publica as linhas. Codigo fora da lista de itens
    /// conhecidos fica depois de todos, e nao no comeco: um totalizador reconhecido pela soma
    /// nao deve furar a fila dos gases.
    /// </summary>
    public static int PublicationOrder(string code)
    {
        int position = 0;

        foreach (MeasuredItem measuredItem in BySourceName.Values)
        {
            if (string.Equals(measuredItem.Code, code, StringComparison.Ordinal)) return position;

            position++;
        }

        return int.MaxValue;
    }

    public static MeasuredItem Resolve(string? sourceName)
    {
        if (BySourceName.TryGetValue(sourceName?.Trim() ?? string.Empty, out MeasuredItem? measuredItem))
            return measuredItem;

        throw new ResultNameOutsideKnownListException(sourceName ?? string.Empty);
    }

    public static bool Knows(string? sourceName)
    {
        return BySourceName.ContainsKey(sourceName?.Trim() ?? string.Empty);
    }

    private static MeasuredItem Gas(string code, string label, string formula, bool isCombustible)
    {
        return new MeasuredItem(
            code,
            label,
            formula,
            "ppm",
            MeasuredItemKind.DissolvedGas,
            isCombustible,
            string.Empty,
            string.Empty);
    }

    private static MeasuredItem Totalizer(string code, string label)
    {
        return new MeasuredItem(
            code,
            label,
            string.Empty,
            "ppm",
            MeasuredItemKind.GasTotalizer,
            false,
            string.Empty,
            string.Empty);
    }

    private static MeasuredItem PhysicalChemicalProperty(
        string code,
        string label,
        string unit,
        string testMethod,
        string regulatoryLimit)
    {
        return new MeasuredItem(
            code,
            label,
            string.Empty,
            unit,
            MeasuredItemKind.PhysicalChemicalProperty,
            false,
            testMethod,
            regulatoryLimit);
    }
}
