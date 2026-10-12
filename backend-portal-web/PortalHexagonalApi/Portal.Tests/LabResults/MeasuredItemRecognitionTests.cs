using Portal.Application.LabResults.Exceptions;
using Portal.Domain.LabResults.Exceptions;
using Portal.Domain.LabResults;

using Xunit;

namespace Portal.Tests.LabResults;

/// <summary>
/// O requisito que esta suite protege: totalizador se reconhece pela soma, nao pelo nome.
/// Ate aqui esse requisito vivia so em comentario, e o codigo dependia inteiramente do
/// rotulo que o laboratorio escreveu. Um rotulo novo na fonte derrubava o laudo por item medido
/// desconhecido, e nenhum teste falhava ao se tirar a aritmetica do caminho.
/// </summary>
public sealed class MeasuredItemRecognitionTests
{
    private const string NameOutsideTheCatalog = "Soma Geral dos Gases";

    [Fact]
    public void ATotalizerWithAnUnknownLabelIsRecognizedByTheSumOfEveryGas()
    {
        IReadOnlyList<Measurement> measurements = MeasuredItemRecognition.Identify([
            Raw("Hidrogênio", "60"),
            Raw("Dióxido de Carbono", "100"),
            Raw(NameOutsideTheCatalog, "160")
        ]);

        Measurement totalizer = measurements
            .Single(measurement => measurement.MeasuredItem.Role == MeasuredItemKind.GasTotalizer);

        Assert.Equal(KnownMeasuredItems.TotalGasesCode, totalizer.MeasuredItem.Code);
        Assert.Equal(NameOutsideTheCatalog, totalizer.MeasuredItem.Label);
        Assert.Equal("ppm", totalizer.MeasuredItem.Unit);
    }

    [Fact]
    public void AnUnknownLabelLandsInTheCombustiblesSlotWhenItIsTheirSum()
    {
        IReadOnlyList<Measurement> measurements = MeasuredItemRecognition.Identify([
            Raw("Hidrogênio", "60"),
            Raw("Dióxido de Carbono", "100"),
            Raw("Somatório dos Inflamáveis", "60")
        ]);

        Measurement totalizer = measurements
            .Single(measurement => measurement.MeasuredItem.Role == MeasuredItemKind.GasTotalizer);

        Assert.Equal(KnownMeasuredItems.CombustibleGasesTotalCode, totalizer.MeasuredItem.Code);
    }

    [Fact]
    public void BothTotalizersMayArriveWithUnknownLabelsAtOnce()
    {
        IReadOnlyList<Measurement> measurements = MeasuredItemRecognition.Identify([
            Raw("Hidrogênio", "60"),
            Raw("Dióxido de Carbono", "100"),
            Raw("Somatório dos Inflamáveis", "60"),
            Raw(NameOutsideTheCatalog, "160")
        ]);

        Assert.Equal(
            [KnownMeasuredItems.CombustibleGasesTotalCode, KnownMeasuredItems.TotalGasesCode],
            measurements
                .Where(measurement => measurement.MeasuredItem.Role == MeasuredItemKind.GasTotalizer)
                .Select(measurement => measurement.MeasuredItem.Code)
                .Order());
    }

    /// <summary>
    /// Valor-limite do reconhecimento: a igualdade e exata. Um a menos e um a mais que a soma
    /// nao sao totalizador, e tratar como se fossem tiraria um gas medido da tabela.
    /// </summary>
    [Theory]
    [InlineData("159")]
    [InlineData("161")]
    public void AValueNextToTheSumIsNotRecognizedAsATotalizer(string publishedValue)
    {
        ResultNameOutsideKnownListException exception = Assert.Throws<ResultNameOutsideKnownListException>(() =>
            MeasuredItemRecognition.Identify([
                Raw("Hidrogênio", "60"),
                Raw("Dióxido de Carbono", "100"),
                Raw(NameOutsideTheCatalog, publishedValue)
            ]));

        Assert.Contains(NameOutsideTheCatalog, exception.Message);
    }

    [Fact]
    public void TheExactSumIsStillRecognizedAtTheBoundary()
    {
        IReadOnlyList<Measurement> measurements = MeasuredItemRecognition.Identify([
            Raw("Hidrogênio", "60"),
            Raw("Dióxido de Carbono", "100"),
            Raw(NameOutsideTheCatalog, "160")
        ]);

        Assert.Contains(measurements, measurement => measurement.MeasuredItem.Role == MeasuredItemKind.GasTotalizer);
    }

    [Theory]
    [InlineData("ND")]
    [InlineData(null)]
    [InlineData("0,01%")]
    public void AnUnknownNameWithoutAPublishedQuantityCannotBeRecognized(string? result)
    {
        Assert.Throws<ResultNameOutsideKnownListException>(() =>
            MeasuredItemRecognition.Identify([
                Raw("Hidrogênio", "60"),
                Raw(NameOutsideTheCatalog, result)
            ]));
    }

    /// <summary>
    /// Com a vaga ja ocupada pelo nome da lista de itens conhecidos, o rotulo desconhecido e recusado em vez
    /// de virar um segundo totalizador do mesmo codigo. Dois itens medidos de codigo igual na
    /// mesma analise sao erro de construcao, e e melhor falhar nomeando a linha que a fonte
    /// trouxe do que falhar falando de codigo repetido.
    /// </summary>
    [Fact]
    public void AnUnknownLabelDoesNotCompeteForATotalizerSlotAlreadyTaken()
    {
        Assert.Throws<ResultNameOutsideKnownListException>(() =>
            MeasuredItemRecognition.Identify([
                Raw("Hidrogênio", "60"),
                Raw("Dióxido de Carbono", "100"),
                Raw("Total de Gases", "160"),
                Raw(NameOutsideTheCatalog, "160")
            ]));
    }

    /// <summary>
    /// Com um gas combustivel so, a soma de todos e a soma dos combustiveis. A vaga do
    /// totalizador geral vence por ser a mais abrangente, e este teste fixa essa escolha
    /// para ela nao mudar sem ninguem perceber.
    /// </summary>
    [Fact]
    public void AnAmbiguousSumStaysInTheOverallTotalizerSlot()
    {
        IReadOnlyList<Measurement> measurements = MeasuredItemRecognition.Identify([
            Raw("Hidrogênio", "60"),
            Raw(NameOutsideTheCatalog, "60")
        ]);

        Measurement totalizer = measurements
            .Single(measurement => measurement.MeasuredItem.Role == MeasuredItemKind.GasTotalizer);

        Assert.Equal(KnownMeasuredItems.TotalGasesCode, totalizer.MeasuredItem.Code);
    }

    [Fact]
    public void AKnownNameStillComesFromTheCatalogWithFormulaAndUnit()
    {
        IReadOnlyList<Measurement> measurements = MeasuredItemRecognition.Identify([
            Raw("Acetileno", "ND")
        ]);

        Measurement acetylene = Assert.Single(measurements);

        Assert.Equal("c2h2", acetylene.MeasuredItem.Code);
        Assert.Equal("C₂H₂", acetylene.MeasuredItem.Formula);
        Assert.True(acetylene.MeasuredItem.IsCombustible);
    }

    private static ResultBeforeRecognition Raw(string sourceName, string? result)
    {
        return new ResultBeforeRecognition(sourceName, MeasuredValue.InterpretResult(result));
    }
}
