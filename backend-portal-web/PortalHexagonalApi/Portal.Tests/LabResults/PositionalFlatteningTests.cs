using Portal.Api.LabResults;
using Portal.Application.LabResults;
using Portal.Domain.LabResults;

using Xunit;

namespace Portal.Tests.LabResults;

public sealed class PositionalFlatteningTests
{
    /// <summary>
    /// O invariante que ja dessincronizou numa massa escrita a mao: cada vetor de valores
    /// tem exatamente o tamanho da lista de colunas. Aqui ele e garantido por construcao,
    /// porque o achatamento percorre as leituras e nunca a lista do item medido.
    /// </summary>
    [Fact]
    public void TheValueVectorHasTheLengthOfTheColumnList()
    {
        HealthIndicatorsResponse response = Flatten(
            PhysicalChemicalAnalysis("01351/2026-004", new DateTime(2026, 7, 6)),
            PhysicalChemicalAnalysis("01352/2026-004", new DateTime(2026, 8, 6)));

        int columns = response.PhysicalChemical.Columns.Count;

        Assert.Equal(2, columns);
        Assert.All(response.PhysicalChemical.Tests, test =>
            Assert.Equal(columns, test.ValuesByAnalysis.Count));
    }

    [Fact]
    public void TheGasDateListHasTheLengthOfTheResultVector()
    {
        HealthIndicatorsResponse response = Flatten(
            GasAnalysis("01417/2026-001", new DateTime(2026, 6, 23)),
            GasAnalysis("01418/2026-001", new DateTime(2026, 7, 23)));

        int dates = response.DissolvedGases.AnalysisDates.Count;

        Assert.Equal(2, dates);
        Assert.All(response.DissolvedGases.Gases, gas => Assert.Equal(dates, gas.Results.Count));
        Assert.Equal(dates, response.DissolvedGases.Analyses.Count);
    }

    [Fact]
    public void ASingleAnalysisHasNoHistoryColumn()
    {
        HealthIndicatorsResponse response = Flatten(
            PhysicalChemicalAnalysis("01351/2026-004", new DateTime(2026, 7, 6)));

        AnalysisColumnResponse only = Assert.Single(response.PhysicalChemical.Columns);

        Assert.True(only.IsCurrentResult);
        Assert.DoesNotContain(response.PhysicalChemical.Columns, column => !column.IsCurrentResult);
    }

    [Fact]
    public void TheMostRecentAnalysisIsTheResultColumn()
    {
        HealthIndicatorsResponse response = Flatten(
            PhysicalChemicalAnalysis("01352/2026-004", new DateTime(2026, 8, 6)),
            PhysicalChemicalAnalysis("01351/2026-004", new DateTime(2026, 7, 6)));

        Assert.Equal(
            ["2026-07-06", "2026-08-06"],
            response.PhysicalChemical.Columns.Select(column => column.Date));
        Assert.True(response.PhysicalChemical.Columns[^1].IsCurrentResult);
    }

    /// <summary>
    /// Texto que nenhum parse aceita vai inteiro para a celula. Virar lacuna afirmaria que o
    /// ensaio nao foi realizado, que e o significado de lacuna neste contrato, e tirar a linha
    /// inteira esconderia que o laboratorio mediu alguma coisa ali.
    /// </summary>
    [Fact]
    public void AnUnreadableCellCarriesTheOriginalTextAndIsDeclaredInTheList()
    {
        SampleAnalysis analysis = AnalysisBuilder.Valid(measurements:
        [
            AnalysisBuilder.MeasurementOf("Teor de Água (ppm m/m) - Medido", "0,01%"),
            AnalysisBuilder.MeasurementOf("Rigidez Dielétrica - Calota (kV)", "48")
        ]);

        HealthIndicatorsResponse response = Flatten(analysis);

        Assert.Equal("0,01%", Assert.Single(TestValues(response, "agua")));
        Assert.Equal(48m, Assert.Single(TestValues(response, "rigidez")));

        UnreadableResultResponse declared = Assert.Single(response.UnreadableResults);
        Assert.Equal("agua", declared.Key);
        Assert.Equal("0,01%", declared.OriginalText);
    }

    /// <summary>
    /// A celula ilegivel degrada a propria celula, nunca a coluna inteira do historico. A
    /// regra ja esteve no adaptador com escopo de serie, e la um unico "0,01%" num laudo
    /// apagava da tabela todas as medicoes boas daquele item medido, em todos os outros laudos
    /// do mesmo equipamento. Um caso de uma analise so nao enxerga isso.
    /// </summary>
    [Fact]
    public void AnUnreadableCellDoesNotEraseTheGoodReadingsOfTheOtherReports()
    {
        HealthIndicatorsResponse response = Flatten(
            RigidityAnalysis("01351/2026-004", new DateTime(2026, 7, 6), "48"),
            RigidityAnalysis("01352/2026-004", new DateTime(2026, 8, 6), "0,01%"));

        Assert.Equal([48m, "0,01%"], TestValues(response, "rigidez"));
    }

    [Fact]
    public void NotDetectedComesOutAsTextAndATestNotPerformedAsNull()
    {
        SampleAnalysis analysis = AnalysisBuilder.Valid(measurements:
        [
            AnalysisBuilder.MeasurementOf("Acetileno", "ND"),
            AnalysisBuilder.MeasurementOf("Hidrogênio", null)
        ]);

        HealthIndicatorsResponse response = Flatten(analysis);

        Assert.Equal("ND", GasResult(response, "c2h2"));
        Assert.Null(GasResult(response, "h2"));
    }

    /// <summary>
    /// A serie do grafico nao distingue nao detectado de ensaio nao realizado: para a linha,
    /// os dois sao buraco. A tabela distingue, e e ela que carrega o "ND".
    /// </summary>
    [Fact]
    public void TheChartSeriesDoesNotCarryTheNotDetected()
    {
        SampleAnalysis analysis = AnalysisBuilder.Valid(measurements:
        [
            AnalysisBuilder.MeasurementOf("Acetileno", "ND")
        ]);

        HealthIndicatorsResponse response = Flatten(analysis);

        Assert.Null(Assert.Single(response.DissolvedGases.Analyses).Values["c2h2"]);
    }

    [Fact]
    public void AReportFromAnAreaWithoutGasReturnsAnEmptyGasBlock()
    {
        HealthIndicatorsResponse response = Flatten(
            PhysicalChemicalAnalysis("01351/2026-004", new DateTime(2026, 7, 6)));

        Assert.Empty(response.DissolvedGases.Gases);
        Assert.Empty(response.DissolvedGases.AnalysisDates);
        Assert.NotEmpty(response.PhysicalChemical.Tests);
    }

    [Fact]
    public void TheTotalizerClosesTheGasTableAfterTheMeasuredGases()
    {
        HealthIndicatorsResponse response = Flatten(
            AnalysisFromTheSample("01417/2026-001"));

        List<string> keys = [.. response.DissolvedGases.Gases.Select(gas => gas.Key)];

        Assert.Equal(["combustiveis", "total"], keys.Skip(keys.Count - 2).Order());
        Assert.All(response.DissolvedGases.Gases.Take(keys.Count - 2),
            gas => Assert.NotEqual(string.Empty, gas.Formula));
    }

    [Fact]
    public void ClassificationAndConformityComeOutNullWhileTheStandardHasNotMigrated()
    {
        HealthIndicatorsResponse response = Flatten(
            PhysicalChemicalAnalysis("01351/2026-004", new DateTime(2026, 7, 6)));

        Assert.Null(response.PhysicalChemical.Conformity);
        Assert.Null(response.Diagnosis);
        Assert.All(response.PhysicalChemical.Tests, test => Assert.Null(test.Classification));
    }

    /// <summary>
    /// A data sai em ISO e o identificador sai opaco, iguais aos do historico de laudos. Eram
    /// dois formatos de data e dois formatos de identificador no mesmo corpo, e com o
    /// protocolo cru a tela nao conseguia casar a analise selecionada com a linha do
    /// historico nem montar um endereco com ela, porque o protocolo tem barra.
    /// </summary>
    [Fact]
    public void TheSelectedAnalysisCarriesTheSameDateAndIdentifierShapeAsTheHistory()
    {
        SampleAnalysis analysis = PhysicalChemicalAnalysis("01351/2026-004", new DateTime(2026, 7, 6));

        HealthIndicatorsResponse response = PositionalFlattening.Flatten(
            new SampleHealthIndicators(
                analysis.SerialNumber,
                analysis.Identification,
                AnalysisSeries.Build(analysis.EquipmentKey, [analysis]),
                AnalysisSeries.Build(analysis.EquipmentKey, []),
                analysis,
                [],
                []),
            "opaque-identifier");

        Assert.NotNull(response.SelectedAnalysis);
        Assert.Equal("2026-07-06", response.SelectedAnalysis.Date);
        Assert.Equal(
            OpaqueIdentifier.Encode("01351/2026-004"),
            response.SelectedAnalysis.Id);
        Assert.All(
            response.PhysicalChemical.Columns,
            column => Assert.Matches(@"^\d{4}-\d{2}-\d{2}$", column.Date));
    }

    private static IReadOnlyList<object?> TestValues(HealthIndicatorsResponse response, string key)
    {
        return response.PhysicalChemical.Tests.Single(test => test.Key == key).ValuesByAnalysis;
    }

    private static SampleAnalysis RigidityAnalysis(
        string protocoloLactec,
        DateTime analysisEndDate,
        string result)
    {
        return AnalysisBuilder.Valid(
            protocoloLactec: protocoloLactec,
            serialNumber: "111.594/1",
            analysisEndDate: analysisEndDate,
            measurements: [AnalysisBuilder.MeasurementOf("Rigidez Dielétrica - Calota (kV)", result)]);
    }

    private static object? GasResult(HealthIndicatorsResponse response, string key)
    {
        return response.DissolvedGases.Gases.Single(gas => gas.Key == key).Results[0];
    }

    private static SampleAnalysis AnalysisFromTheSample(string protocoloLactec)
    {
        return Portal.Adapters.LabResults.EmbeddedSampleLabTestRepository.ReadWholeSample()
            .Single(analysis => analysis.ProtocoloLactec == protocoloLactec);
    }

    private static SampleAnalysis PhysicalChemicalAnalysis(string protocoloLactec, DateTime analysisEndDate)
    {
        return AnalysisBuilder.Valid(
            protocoloLactec: protocoloLactec,
            serialNumber: "111.594/1",
            analysisEndDate: analysisEndDate,
            measurements:
            [
                AnalysisBuilder.MeasurementOf("Rigidez Dielétrica - Calota (kV)", "48"),
                AnalysisBuilder.MeasurementOf("Tensão Interfacial (dina/cm)", null)
            ]);
    }

    private static SampleAnalysis GasAnalysis(string protocoloLactec, DateTime analysisEndDate)
    {
        return AnalysisBuilder.Valid(
            protocoloLactec: protocoloLactec,
            analysisEndDate: analysisEndDate,
            measurements:
            [
                AnalysisBuilder.MeasurementOf("Hidrogênio", "60"),
                AnalysisBuilder.MeasurementOf("Acetileno", "ND")
            ]);
    }

    private static HealthIndicatorsResponse Flatten(params SampleAnalysis[] analyses)
    {
        string equipmentKey = analyses[0].EquipmentKey;

        return PositionalFlattening.Flatten(
            new SampleHealthIndicators(
                analyses[0].SerialNumber,
                analyses[0].Identification,
                AnalysisSeries.Build(
                    equipmentKey,
                    analyses.Where(analysis => analysis.HasPhysicalChemicalProperty)),
                AnalysisSeries.Build(
                    equipmentKey,
                    analyses.Where(analysis => analysis.HasDissolvedGas)),
                null,
                [.. analyses.SelectMany(analysis => analysis.Measurements
                    .Where(measurement => measurement.Value is MeasuredValue.UninterpretedText)
                    .Select(measurement => new UnreadableResult(
                        measurement.MeasuredItem.Code,
                        measurement.MeasuredItem.Label,
                        ((MeasuredValue.UninterpretedText)measurement.Value).OriginalText,
                        analysis.ProtocoloLactec)))],
                []),
            "opaque-identifier");
    }
}
