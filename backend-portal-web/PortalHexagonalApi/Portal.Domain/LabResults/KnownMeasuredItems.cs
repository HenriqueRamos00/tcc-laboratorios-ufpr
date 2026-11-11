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
            LimitNotCuratedYet),
        ["Aspecto visual em óleo mineral isolante"] = PhysicalChemicalProperty(
            "aspectoVisual",
            "Aspecto visual em óleo mineral isolante",
            MethodNotCuratedYet,
            MethodNotCuratedYet,
            LimitNotCuratedYet),
        ["Composição Ca"] = PhysicalChemicalProperty(
            "composicaoCa",
            "Composição Ca",
            MethodNotCuratedYet,
            MethodNotCuratedYet,
            LimitNotCuratedYet),
        ["Composição Cn"] = PhysicalChemicalProperty(
            "composicaoCn",
            "Composição Cn",
            MethodNotCuratedYet,
            MethodNotCuratedYet,
            LimitNotCuratedYet),
        ["Composição Cp"] = PhysicalChemicalProperty(
            "composicaoCp",
            "Composição Cp",
            MethodNotCuratedYet,
            MethodNotCuratedYet,
            LimitNotCuratedYet),
        ["Contagem de Particulas (OMI) > 10 μm"] = PhysicalChemicalProperty(
            "particulas10Micrometros",
            "Contagem de Particulas (OMI) > 10 μm",
            MethodNotCuratedYet,
            MethodNotCuratedYet,
            LimitNotCuratedYet),
        ["Contagem de Particulas (OMI) > 14 μm"] = PhysicalChemicalProperty(
            "particulas14Micrometros",
            "Contagem de Particulas (OMI) > 14 μm",
            MethodNotCuratedYet,
            MethodNotCuratedYet,
            LimitNotCuratedYet),
        ["Contagem de Particulas (OMI) > 21 μm"] = PhysicalChemicalProperty(
            "particulas21Micrometros",
            "Contagem de Particulas (OMI) > 21 μm",
            MethodNotCuratedYet,
            MethodNotCuratedYet,
            LimitNotCuratedYet),
        ["Contagem de Particulas (OMI) > 25 μm"] = PhysicalChemicalProperty(
            "particulas25Micrometros",
            "Contagem de Particulas (OMI) > 25 μm",
            MethodNotCuratedYet,
            MethodNotCuratedYet,
            LimitNotCuratedYet),
        ["Contagem de Particulas (OMI) > 38 μm"] = PhysicalChemicalProperty(
            "particulas38Micrometros",
            "Contagem de Particulas (OMI) > 38 μm",
            MethodNotCuratedYet,
            MethodNotCuratedYet,
            LimitNotCuratedYet),
        ["Contagem de Particulas (OMI) > 4 μm"] = PhysicalChemicalProperty(
            "particulas4Micrometros",
            "Contagem de Particulas (OMI) > 4 μm",
            MethodNotCuratedYet,
            MethodNotCuratedYet,
            LimitNotCuratedYet),
        ["Contagem de Particulas (OMI) > 6 μm"] = PhysicalChemicalProperty(
            "particulas6Micrometros",
            "Contagem de Particulas (OMI) > 6 μm",
            MethodNotCuratedYet,
            MethodNotCuratedYet,
            LimitNotCuratedYet),
        ["Contagem de Particulas (OMI) > 70 μm"] = PhysicalChemicalProperty(
            "particulas70Micrometros",
            "Contagem de Particulas (OMI) > 70 μm",
            MethodNotCuratedYet,
            MethodNotCuratedYet,
            LimitNotCuratedYet),
        ["Contagem de Particulas (OMI) ISO"] = PhysicalChemicalProperty(
            "particulasIso",
            "Contagem de Particulas (OMI) ISO",
            MethodNotCuratedYet,
            MethodNotCuratedYet,
            LimitNotCuratedYet),
        ["Densidade A 15,6°C"] = PhysicalChemicalProperty(
            "densidade156",
            "Densidade A 15,6°C",
            MethodNotCuratedYet,
            MethodNotCuratedYet,
            LimitNotCuratedYet),
        ["Determinação da Cor em amostras de óleo Lubrificante"] = PhysicalChemicalProperty(
            "corLubrificante",
            "Determinação da Cor em amostras de óleo Lubrificante",
            MethodNotCuratedYet,
            MethodNotCuratedYet,
            LimitNotCuratedYet),
        ["Determinação das características emulsionantes em amostras de óleo Lubrificante"] = PhysicalChemicalProperty(
            "emulsaoLubrificante",
            "Determinação das características emulsionantes em amostras de óleo Lubrificante",
            MethodNotCuratedYet,
            MethodNotCuratedYet,
            LimitNotCuratedYet),
        ["Determinação das Características espumantes (sequência II) em amostras de óleo Lubrificante"] = PhysicalChemicalProperty(
            "espumaLubrificante",
            "Determinação das Características espumantes (sequência II) em amostras de óleo Lubrificante",
            MethodNotCuratedYet,
            MethodNotCuratedYet,
            LimitNotCuratedYet),
        ["Determinação de 2-Furfuraldeído (2-Fal) em óleo mineral isolante"] = PhysicalChemicalProperty(
            "furfuraldeido",
            "Determinação de 2-Furfuraldeído (2-Fal) em óleo mineral isolante",
            MethodNotCuratedYet,
            MethodNotCuratedYet,
            LimitNotCuratedYet),
        ["Determinação de Índice de Neutralização em amostras de Óleo Lubrificante"] = PhysicalChemicalProperty(
            "neutralizacaoLubrificante",
            "Determinação de Índice de Neutralização em amostras de Óleo Lubrificante",
            MethodNotCuratedYet,
            MethodNotCuratedYet,
            LimitNotCuratedYet),
        ["Determinação de Teor de Água por centrifugação em amostras de Óleo Lubrificante"] = PhysicalChemicalProperty(
            "aguaLubrificante",
            "Determinação de Teor de Água por centrifugação em amostras de Óleo Lubrificante",
            MethodNotCuratedYet,
            MethodNotCuratedYet,
            LimitNotCuratedYet),
        ["Determinação de Teor de Passivador: BTA e IRGAMET em Óleo Mineral Isolante"] = PhysicalChemicalProperty(
            "passivadorBtaIrgamet",
            "Determinação de Teor de Passivador: BTA e IRGAMET em Óleo Mineral Isolante",
            MethodNotCuratedYet,
            MethodNotCuratedYet,
            LimitNotCuratedYet),
        ["Determinação de Teor de Sedimentos por centrifugação em amostras de Óleo Lubrificante"] = PhysicalChemicalProperty(
            "sedimentosLubrificante",
            "Determinação de Teor de Sedimentos por centrifugação em amostras de Óleo Lubrificante",
            MethodNotCuratedYet,
            MethodNotCuratedYet,
            LimitNotCuratedYet),
        ["Determinação de Teor Inibidor de Oxidação BHT (DBPC)"] = PhysicalChemicalProperty(
            "inibidorDbpc",
            "Determinação de Teor Inibidor de Oxidação BHT (DBPC)",
            MethodNotCuratedYet,
            MethodNotCuratedYet,
            LimitNotCuratedYet),
        ["Determinação de Viscosidade Cinemática a 40 °C em amostras de Óleo Lubrificante"] = PhysicalChemicalProperty(
            "viscosidadeLubrificante",
            "Determinação de Viscosidade Cinemática a 40 °C em amostras de Óleo Lubrificante",
            MethodNotCuratedYet,
            MethodNotCuratedYet,
            LimitNotCuratedYet),
        ["Determinação do Teor de Dibenzil Dissulfeto (DBDS) em óleo mineral isolante"] = PhysicalChemicalProperty(
            "dbds",
            "Determinação do Teor de Dibenzil Dissulfeto (DBDS) em óleo mineral isolante",
            MethodNotCuratedYet,
            MethodNotCuratedYet,
            LimitNotCuratedYet),
        ["Determinação quantitativa de bifenilas policloradas (PCB) em óleo mineral isolante"] = PhysicalChemicalProperty(
            "pcbOleoMineral",
            "Determinação quantitativa de bifenilas policloradas (PCB) em óleo mineral isolante",
            MethodNotCuratedYet,
            MethodNotCuratedYet,
            LimitNotCuratedYet),
        ["Determinação quantitativa de bifenilas policloradas (PCB) em óleo vegetal isolante"] = PhysicalChemicalProperty(
            "pcbOleoVegetal",
            "Determinação quantitativa de bifenilas policloradas (PCB) em óleo vegetal isolante",
            MethodNotCuratedYet,
            MethodNotCuratedYet,
            LimitNotCuratedYet),
        ["Ensaio De Cor em Óleo Mineral Isolante"] = PhysicalChemicalProperty(
            "cor",
            "Ensaio De Cor em Óleo Mineral Isolante",
            MethodNotCuratedYet,
            MethodNotCuratedYet,
            LimitNotCuratedYet),
        ["Ensaio de Enxofre Corrosivo em Óleo Mineral Isolante"] = PhysicalChemicalProperty(
            "enxofreCorrosivo",
            "Ensaio de Enxofre Corrosivo em Óleo Mineral Isolante",
            MethodNotCuratedYet,
            MethodNotCuratedYet,
            LimitNotCuratedYet),
        ["Ensaio Índice de Refração a 20ºC em Óleo Mineral Isolante"] = PhysicalChemicalProperty(
            "indiceDeRefracao",
            "Ensaio Índice de Refração a 20ºC em Óleo Mineral Isolante",
            MethodNotCuratedYet,
            MethodNotCuratedYet,
            LimitNotCuratedYet),
        ["Ensaio Ponto de Anilina em Óleo Mineral Isolante"] = PhysicalChemicalProperty(
            "pontoDeAnilina",
            "Ensaio Ponto de Anilina em Óleo Mineral Isolante",
            MethodNotCuratedYet,
            MethodNotCuratedYet,
            LimitNotCuratedYet),
        ["Ensaio Ponto de Combustão em Óleo Mineral Isolante"] = PhysicalChemicalProperty(
            "pontoDeCombustao",
            "Ensaio Ponto de Combustão em Óleo Mineral Isolante",
            MethodNotCuratedYet,
            MethodNotCuratedYet,
            LimitNotCuratedYet),
        ["Ensaio Ponto de Fulgor em Óleo Mineral Isolante"] = PhysicalChemicalProperty(
            "pontoDeFulgor",
            "Ensaio Ponto de Fulgor em Óleo Mineral Isolante",
            MethodNotCuratedYet,
            MethodNotCuratedYet,
            LimitNotCuratedYet),
        ["Ensaio Viscosidade Cinemática a 40ºC em Óleo Mineral Isolante"] = PhysicalChemicalProperty(
            "viscosidade",
            "Ensaio Viscosidade Cinemática a 40ºC em Óleo Mineral Isolante",
            MethodNotCuratedYet,
            MethodNotCuratedYet,
            LimitNotCuratedYet),
        ["Estabilidade à oxidação - BORRA"] = PhysicalChemicalProperty(
            "estabilidadeAOxidacaoBorra",
            "Estabilidade à oxidação - BORRA",
            MethodNotCuratedYet,
            MethodNotCuratedYet,
            LimitNotCuratedYet),
        ["Ponto de Fluidez em Óleo Mineral Isolante"] = PhysicalChemicalProperty(
            "pontoDeFluidez",
            "Ponto de Fluidez em Óleo Mineral Isolante",
            MethodNotCuratedYet,
            MethodNotCuratedYet,
            LimitNotCuratedYet),
        ["Rigidez Dielétrica - disco (kV)"] = PhysicalChemicalProperty(
            "rigidezDisco",
            "Rigidez Dielétrica - disco (kV)",
            "kV",
            MethodNotCuratedYet,
            LimitNotCuratedYet),
        ["Teor de Água (ppm m/m)"] = PhysicalChemicalProperty(
            "aguaPpm",
            "Teor de Água (ppm m/m)",
            "ppm m/m",
            MethodNotCuratedYet,
            LimitNotCuratedYet),
        ["Teor de PCB - Conforme Laudo"] = PhysicalChemicalProperty(
            "pcbConformeLaudo",
            "Teor de PCB - Conforme Laudo",
            MethodNotCuratedYet,
            MethodNotCuratedYet,
            LimitNotCuratedYet),
        ["Teor Total de Passivador (mg/kg)"] = PhysicalChemicalProperty(
            "passivadorTotal",
            "Teor Total de Passivador (mg/kg)",
            "mg/kg",
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
