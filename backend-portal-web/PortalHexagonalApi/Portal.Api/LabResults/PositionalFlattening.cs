using Portal.Application.LabResults;
using Portal.Domain.LabResults;

namespace Portal.Api.LabResults;

/// <summary>
/// Achata a serie historica no formato posicional que a tela consome: uma lista de colunas,
/// e um vetor de valores por item medido que casa indice a indice com essas colunas.
///
/// Mora na borda porque e decisao de layout de tabela de um ator so. O dominio entrega
/// valor por codigo de item medido, autodescritivo; o indice nasce aqui e morre aqui, e e aqui
/// que o invariante entre numero de colunas e tamanho do vetor pode ser garantido por
/// construcao, percorrendo sempre as leituras e nunca a lista do item medido.
/// </summary>
public static class PositionalFlattening
{
    private const string NotDetectedText = "ND";
    private const string NormalResultLabel = "NORMAL";
    private const string NormalConformity = "normal";
    private const string NonConformingConformity = "nao-conforme";

    public static HealthIndicatorsResponse Flatten(
        SampleHealthIndicators indicators,
        string opaqueIdentifier)
    {
        ArgumentNullException.ThrowIfNull(indicators);

        return new HealthIndicatorsResponse
        {
            EquipmentId = opaqueIdentifier,
            Tag = indicators.Tag ?? indicators.SerialNumber ?? indicators.Identification,
            SelectedAnalysis = FindSelectedAnalysis(indicators.SelectedAnalysis),
            PhysicalChemical = BuildPhysicalChemicalBlock(indicators),
            DissolvedGases = BuildGasBlock(indicators),
            OilType = new OilTypeResponse { Kind = indicators.Oil.Kind, Reported = indicators.Oil.Reported },
            Diagnosis = null,
            UnreadableResults = [.. indicators.UnreadableResults.Select(measuredItem =>
                new UnreadableResultResponse
                {
                    Key = measuredItem.Code,
                    Name = measuredItem.Label,
                    OriginalText = measuredItem.OriginalText,
                    ProtocoloLactec = measuredItem.ProtocoloLactec,
                    AnalysisId = measuredItem.AnalysisId,
                    Unit = measuredItem.Unit,
                    Method = measuredItem.Method,
                    ReferenceLimit = measuredItem.ReferenceLimit,
                    ResultKind = measuredItem.ResultKind
                })],
            TotalizerChecks = [.. indicators.TotalizerChecks.Select(check =>
                new TotalizerCheckResponse
                {
                    Key = check.TotalizerCode,
                    PublishedValue = check.PublishedValue,
                    MeasuredSum = check.MeasuredSum,
                    GasesWithoutTest = check.GasesWithoutTest,
                    Result = check.Result
                })]
        };
    }

    private static SelectedAnalysisResponse? FindSelectedAnalysis(SampleAnalysis? analysis)
    {
        if (analysis is null) return null;

        return new SelectedAnalysisResponse
        {
            Id = OpaqueIdentifier.Encode(analysis.AnalysisId),
            Date = WireDate.From(analysis.ReportDate),
            Label = analysis.Identification,
            HasPhysicalChemical = analysis.HasPhysicalChemicalProperty,
            HasDissolvedGases = analysis.HasDissolvedGas
        };
    }

    private static PhysicalChemicalBlockResponse BuildPhysicalChemicalBlock(SampleHealthIndicators indicators)
    {
        AnalysisSeries series = indicators.PhysicalChemicalSeries;

        return new PhysicalChemicalBlockResponse
        {
            Conformity = ConformityOf(indicators.PhysicalChemicalResult),
            Verdict = indicators.PhysicalChemicalResult,
            NominalVoltage = string.IsNullOrWhiteSpace(indicators.NominalVoltage) ? null : indicators.NominalVoltage,
            Columns = BuildColumns(series),
            Analyses = BuildReadings(series),
            Tests = [.. series.ItemsOf(MeasuredItemKind.PhysicalChemicalProperty)
                .Select(measuredItem => TestOf(series, measuredItem, indicators.Oil.LimitOf(measuredItem)))],
            Reports = ReportsOf(indicators.PhysicalChemicalReports)
        };
    }

    private static PhysicalChemicalTestResponse TestOf(
        AnalysisSeries series,
        MeasuredItem measuredItem,
        PhysicalChemicalLimit limit)
    {
        return new PhysicalChemicalTestResponse
        {
            Key = measuredItem.Code,
            Name = measuredItem.Label,
            Method = measuredItem.TestMethod,
            Limit = limit.Value,
            LimitAbsence = limit.Absence,
            ValuesByAnalysis = BuildItemValues(series, measuredItem.Code),
            Classification = null
        };
    }

    private static DissolvedGasBlockResponse BuildGasBlock(SampleHealthIndicators indicators)
    {
        AnalysisSeries series = indicators.GasSeries;
        List<MeasuredItem> gases = [.. series.ItemsOf(MeasuredItemKind.DissolvedGas)];
        List<MeasuredItem> totalizers = [.. series.ItemsOf(MeasuredItemKind.GasTotalizer)];

        return new DissolvedGasBlockResponse
        {
            Conformity = ConformityOf(indicators.GasResult),
            Verdict = indicators.GasResult,
            AnalysisDates = [.. series.Readings.Select(DateOf)],
            Analyses = BuildReadings(series),
            Gases = [.. gases.Concat(totalizers).Select(measuredItem => new DissolvedGasResponse
            {
                Key = measuredItem.Code,
                Name = measuredItem.Label,
                Formula = measuredItem.Formula,
                Results = BuildItemValues(series, measuredItem.Code)
            })],
            Reports = ReportsOf(indicators.GasReports)
        };
    }

    private static IReadOnlyList<PublishedReportResponse> ReportsOf(IReadOnlyList<ReportConclusion> conclusions)
    {
        return [.. conclusions.Select(conclusion => new PublishedReportResponse
        {
            Identification = conclusion.Identification,
            Date = WireDate.From(conclusion.Date),
            ResultLabel = conclusion.ResultLabel,
            Title = conclusion.Title,
            Paragraphs = conclusion.Paragraphs
        })];
    }

    private static string? ConformityOf(string? resultLabel)
    {
        if (string.IsNullOrWhiteSpace(resultLabel)) return null;

        return string.Equals(resultLabel.Trim(), NormalResultLabel, StringComparison.OrdinalIgnoreCase)
            ? NormalConformity
            : NonConformingConformity;
    }

    /// <summary>
    /// A analise mais recente e a coluna de resultado; as anteriores formam o historico. Com
    /// uma leitura so, a lista de historico fica vazia, e a tela ja trata esse caso.
    /// </summary>
    private static IReadOnlyList<AnalysisColumnResponse> BuildColumns(AnalysisSeries series)
    {
        int last = series.Readings.Count - 1;

        return [.. series.Readings.Select((reading, index) => new AnalysisColumnResponse
        {
            Date = DateOf(reading),
            IsCurrentResult = index == last
        })];
    }

    /// <summary>
    /// A serie que alimenta o grafico nao distingue nao detectado de ensaio nao realizado:
    /// para a linha, os dois sao buraco. A tabela distingue, e e ela que carrega o "ND".
    /// </summary>
    private static IReadOnlyList<SeriesReadingResponse> BuildReadings(AnalysisSeries series)
    {
        return [.. series.Readings.Select(reading => new SeriesReadingResponse
        {
            Date = DateOf(reading),
            Values = reading.ValuesByItemCode.ToDictionary(
                pair => pair.Key,
                pair => pair.Value is MeasuredValue.Quantity quantity ? quantity.NumericValue : (decimal?)null,
                StringComparer.Ordinal)
        })];
    }

    /// <summary>
    /// Percorre as leituras, nunca a lista do item medido: e isso que garante que o vetor tenha
    /// o mesmo tamanho da lista de colunas, em vez de depender de quem escreveu a massa.
    /// </summary>
    private static IReadOnlyList<object?> BuildItemValues(AnalysisSeries series, string code)
    {
        return [.. series.Readings.Select(reading =>
            reading.ValuesByItemCode.TryGetValue(code, out MeasuredValue? value)
                ? SerializeValue(value)
                : null)];
    }

    /// <summary>
    /// No fio o valor tem as mesmas quatro formas do dominio: numero, "ND", o texto cru que
    /// nenhum parse aceitou, e nulo para ensaio nao realizado.
    ///
    /// O texto cru vai inteiro para a celula. Mandar lacuna no lugar dele seria afirmar que
    /// o ensaio nao foi realizado, que e o significado de lacuna neste contrato. A lista de
    /// <see cref="HealthIndicatorsResponse.UnreadableResults"/> repete o caso com rotulo
    /// e protocolo, para quem le a resposta saber de qual laudo aquela celula veio.
    /// </summary>
    private static object? SerializeValue(MeasuredValue value)
    {
        return value switch
        {
            MeasuredValue.Quantity quantity => quantity.NumericValue,
            MeasuredValue.NotDetected => NotDetectedText,
            MeasuredValue.UninterpretedText unreadable => unreadable.OriginalText,
            _ => null
        };
    }

    private static string DateOf(SeriesReading reading)
    {
        return WireDate.From(reading.ReportDate);
    }
}
