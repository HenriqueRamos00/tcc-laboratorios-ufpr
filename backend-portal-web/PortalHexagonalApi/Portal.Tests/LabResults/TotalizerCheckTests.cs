using Portal.Adapters.LabResults;
using Portal.Domain.LabResults;

using Xunit;

namespace Portal.Tests.LabResults;

/// <summary>
/// A amostra real tem tres protocolos de cromatografia, e nos tres a soma dos gases fecha
/// exata com os dois totalizadores publicados, com nao detectado valendo zero.
/// </summary>
public sealed class TotalizerCheckTests
{
    [Theory]
    [InlineData("01417/2026-001", "total", 31063)]
    [InlineData("01417/2026-001", "combustiveis", 449)]
    [InlineData("01718/2026-005", "total", 95463)]
    [InlineData("01718/2026-005", "combustiveis", 1687)]
    [InlineData("01563/2026-018", "total", 89862)]
    [InlineData("01563/2026-018", "combustiveis", 62)]
    [InlineData("01439/2026-004", "total", 10782)]
    [InlineData("01439/2026-004", "combustiveis", 12)]
    public void APublishedTotalizerClosesOnTheSumOfTheGases(
        string protocoloLactec,
        string totalizerCode,
        decimal expectedValue)
    {
        SampleAnalysis analysis = AnalysisFromTheSample(protocoloLactec);

        TotalizerCheck check = analysis.CheckTotalizers()
            .Single(item => item.TotalizerCode == totalizerCode);

        Assert.Equal(expectedValue, check.PublishedValue);
        Assert.Equal(expectedValue, check.MeasuredSum);
        Assert.Equal(TotalizerCheckResult.Matches, check.Result);
    }

    [Fact]
    public void NotDetectedCountsAsZeroInTheTotalizerSum()
    {
        SampleAnalysis analysis = AnalysisBuilder.Valid(measurements:
        [
            AnalysisBuilder.MeasurementOf("Hidrogênio", "60"),
            AnalysisBuilder.MeasurementOf("Acetileno", "ND"),
            AnalysisBuilder.MeasurementOf("Total de Gases Combustíveis", "60")
        ]);

        TotalizerCheck check = Assert.Single(analysis.CheckTotalizers());

        Assert.Equal(TotalizerCheckResult.Matches, check.Result);
        Assert.Equal(0, check.GasesWithoutTest);
        Assert.Equal(60m, check.MeasuredSum);
    }

    /// <summary>
    /// Com gas sem ensaio a soma nao e comparavel, e o desfecho nao e divergente. Chamar de
    /// divergente acusaria o laboratorio de errar uma conta que nao fez, e e exatamente o
    /// que um veredito booleano obrigava a fazer.
    /// </summary>
    [Fact]
    public void AGasWithoutATestLeavesTheSumIncompleteInsteadOfDivergent()
    {
        SampleAnalysis analysis = AnalysisBuilder.Valid(measurements:
        [
            AnalysisBuilder.MeasurementOf("Hidrogênio", "60"),
            AnalysisBuilder.MeasurementOf("Metano", null),
            AnalysisBuilder.MeasurementOf("Total de Gases Combustíveis", "137")
        ]);

        TotalizerCheck check = Assert.Single(analysis.CheckTotalizers());

        Assert.Equal(TotalizerCheckResult.IncompleteSum, check.Result);
        Assert.Equal(1, check.GasesWithoutTest);
        Assert.Equal(60m, check.MeasuredSum);
        Assert.Equal(137m, check.PublishedValue);
    }

    /// <summary>
    /// A soma incompleta vence mesmo quando o publicado bate com a soma parcial: bater com
    /// soma a que falta parcela e coincidencia, e publicar "confere" ali afirmaria uma
    /// exatidao que ninguem mediu.
    /// </summary>
    [Fact]
    public void APartialSumThatMatchesThePublishedValueIsStillAnIncompleteSum()
    {
        SampleAnalysis analysis = AnalysisBuilder.Valid(measurements:
        [
            AnalysisBuilder.MeasurementOf("Hidrogênio", "60"),
            AnalysisBuilder.MeasurementOf("Metano", null),
            AnalysisBuilder.MeasurementOf("Total de Gases Combustíveis", "60")
        ]);

        TotalizerCheck check = Assert.Single(analysis.CheckTotalizers());

        Assert.Equal(TotalizerCheckResult.IncompleteSum, check.Result);
        Assert.Equal(60m, check.MeasuredSum);
        Assert.Equal(60m, check.PublishedValue);
    }

    [Theory]
    [InlineData("59", TotalizerCheckResult.Diverges)]
    [InlineData("60", TotalizerCheckResult.Matches)]
    [InlineData("61", TotalizerCheckResult.Diverges)]
    public void TheBoundaryValueOfTheTotalizerSeparatesMatchesFromDiverges(
        string publishedTotal,
        TotalizerCheckResult expectedResult)
    {
        SampleAnalysis analysis = AnalysisBuilder.Valid(measurements:
        [
            AnalysisBuilder.MeasurementOf("Hidrogênio", "60"),
            AnalysisBuilder.MeasurementOf("Total de Gases Combustíveis", publishedTotal)
        ]);

        TotalizerCheck check = Assert.Single(analysis.CheckTotalizers());

        Assert.Equal(expectedResult, check.Result);
    }

    [Fact]
    public void CarbonDioxideDoesNotEnterTheCombustibleGasesTotal()
    {
        SampleAnalysis analysis = AnalysisFromTheSample("01417/2026-001");

        MeasuredValue.Quantity dioxide = Assert.IsType<MeasuredValue.Quantity>(analysis.ValueOf("co2"));
        TotalizerCheck combustibles = analysis.CheckTotalizers()
            .Single(item => item.TotalizerCode == "combustiveis");

        Assert.True(dioxide.NumericValue > 0);
        Assert.Equal(449m, combustibles.MeasuredSum);
    }

    [Fact]
    public void ATotalizerWithoutAPublishedValueIsNotChecked()
    {
        SampleAnalysis analysis = AnalysisBuilder.Valid(measurements:
        [
            AnalysisBuilder.MeasurementOf("Hidrogênio", "60"),
            AnalysisBuilder.MeasurementOf("Total de Gases", null)
        ]);

        Assert.Empty(analysis.CheckTotalizers());
    }

    private static SampleAnalysis AnalysisFromTheSample(string protocoloLactec)
    {
        return EmbeddedSampleLabTestRepository.ReadWholeSample()
            .Single(analysis => analysis.ProtocoloLactec == protocoloLactec);
    }
}
