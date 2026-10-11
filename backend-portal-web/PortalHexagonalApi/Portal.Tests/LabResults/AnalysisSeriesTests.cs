using Portal.Domain.LabResults;

using Xunit;

namespace Portal.Tests.LabResults;

public sealed class AnalysisSeriesTests
{
    /// <summary>
    /// Dois laudos de transformadores diferentes, os dois com a serie em branco, tem chaves
    /// de equipamento diferentes e nao formam uma serie so. Quando a comparacao era pelo
    /// numero de serie cru, nulo era igual a nulo e os dois entravam juntos.
    /// </summary>
    [Fact]
    public void TwoReportsWithoutASerialNumberDoNotEnterTheSameSeries()
    {
        SampleAnalysis first = AnalysisBuilder.Valid(
            protocoloLactec: "01439/2026-004",
            serialNumber: null);
        SampleAnalysis second = AnalysisBuilder.Valid(
            protocoloLactec: "01482/2026-001",
            serialNumber: "   ");

        Assert.Throws<ArgumentException>(() => AnalysisSeries.Build(
            first.EquipmentKey,
            [first, second]));
    }

    /// <summary>
    /// Sem numero de serie a chave cai no protocolo, e o laudo continua enderecavel. Antes
    /// ele existia na fonte e respondia 404 na tela.
    /// </summary>
    [Fact]
    public void AReportWithoutASerialNumberIsAddressableByItsProtocol()
    {
        SampleAnalysis only = AnalysisBuilder.Valid(
            protocoloLactec: "01439/2026-004",
            serialNumber: null);

        AnalysisSeries series = AnalysisSeries.Build("01439/2026-004", [only]);

        Assert.Equal("01439/2026-004", series.EquipmentKey);
        Assert.Single(series.Readings);
    }

    /// <summary>
    /// Com numero de serie a chave e a serie, e nao o protocolo: e o que mantem varios
    /// laudos do mesmo transformador numa serie historica so.
    /// </summary>
    [Fact]
    public void WithASerialNumberTheKeyStaysTheSerialNumber()
    {
        SampleAnalysis analysis = AnalysisBuilder.Valid(serialNumber: "111.594/1");

        Assert.Equal("111.594/1", analysis.EquipmentKey);
    }

    [Fact]
    public void AnAnalysisOfAnotherSerialNumberDoesNotEnterTheSeries()
    {
        SampleAnalysis stranger = AnalysisBuilder.Valid(serialNumber: "999999");

        Assert.Throws<ArgumentException>(() => AnalysisSeries.Build("571289", [stranger]));
    }

    [Fact]
    public void ReadingsComeOutOrderedByAnalysisEndDate()
    {
        SampleAnalysis newer = AnalysisBuilder.Valid(
            protocoloLactec: "01500/2026-001",
            analysisEndDate: new DateTime(2026, 8, 1));
        SampleAnalysis older = AnalysisBuilder.Valid(
            protocoloLactec: "01400/2026-001",
            analysisEndDate: new DateTime(2026, 2, 1));

        AnalysisSeries series = AnalysisSeries.Build("571289", [newer, older]);

        Assert.Equal(
            ["01400/2026-001", "01500/2026-001"],
            series.Readings.Select(reading => reading.ProtocoloLactec));
    }

    [Fact]
    public void ASeriesWithoutAnyAnalysisIsEmpty()
    {
        AnalysisSeries series = AnalysisSeries.Build("571289", []);

        Assert.True(series.IsEmpty);
        Assert.Empty(series.Readings);
    }

    [Fact]
    public void ReadingValuesComeIndexedByItemCode()
    {
        SampleAnalysis analysis = AnalysisBuilder.Valid(measurements:
        [
            AnalysisBuilder.MeasurementOf("Hidrogênio", "60"),
            AnalysisBuilder.MeasurementOf("Acetileno", "ND")
        ]);

        SeriesReading reading = Assert.Single(AnalysisSeries.Build("571289", [analysis]).Readings);

        Assert.IsType<MeasuredValue.Quantity>(reading.ValuesByItemCode["h2"]);
        Assert.IsType<MeasuredValue.NotDetected>(reading.ValuesByItemCode["c2h2"]);
    }
}
