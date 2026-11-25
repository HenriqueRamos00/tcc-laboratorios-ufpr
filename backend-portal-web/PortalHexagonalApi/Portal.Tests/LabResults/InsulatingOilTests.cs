using Portal.Api.LabResults;
using Portal.Application.LabResults;
using Portal.Domain.LabResults;

using Xunit;

namespace Portal.Tests.LabResults;

public sealed class InsulatingOilTests
{
    [Theory]
    [InlineData("Óleo Mineral Isolante", InsulatingOilKind.Mineral)]
    [InlineData("Mineral", InsulatingOilKind.Mineral)]
    [InlineData("ÓLEO VEGETAL ISOLANTE", InsulatingOilKind.NaturalEster)]
    [InlineData("Éster natural", InsulatingOilKind.NaturalEster)]
    [InlineData("Fluido de silicone", InsulatingOilKind.Silicone)]
    [InlineData("Éster sintético", InsulatingOilKind.Unrecognized)]
    [InlineData("Ascarel", InsulatingOilKind.Unrecognized)]
    [InlineData(null, InsulatingOilKind.NotInformed)]
    [InlineData("  ", InsulatingOilKind.NotInformed)]
    public void TheReportedTextIsRecognizedWithoutAssumingMineral(string? reported, InsulatingOilKind expected)
    {
        Assert.Equal(expected, InsulatingOil.Recognize(reported).Kind);
    }

    [Fact]
    public void MineralOilKeepsTheCuratedLimit()
    {
        HealthIndicatorsResponse response = Flatten("Óleo Mineral Isolante");

        PhysicalChemicalTestResponse rigidity = response.PhysicalChemical.Tests.Single(test => test.Key == "rigidez");

        Assert.Equal("min. 40", rigidity.Limit);
        Assert.Null(rigidity.LimitAbsence);
        Assert.Equal(InsulatingOilKind.Mineral, response.OilType.Kind);
        Assert.Equal("Óleo Mineral Isolante", response.OilType.Reported);
    }

    [Theory]
    [InlineData("Óleo Vegetal Isolante")]
    [InlineData("Silicone")]
    public void AnotherOilTypeNeverBorrowsTheMineralLimit(string reported)
    {
        HealthIndicatorsResponse response = Flatten(reported);

        Assert.All(response.PhysicalChemical.Tests, test =>
        {
            Assert.Null(test.Limit);
            Assert.Equal(PhysicalChemicalLimitAbsence.OilTypeWithoutCriterion, test.LimitAbsence);
        });
    }

    [Fact]
    public void AnUnknownOilTypeHasNoLimitAndSaysTheTypeWasNotInformed()
    {
        HealthIndicatorsResponse response = Flatten(null);

        Assert.Equal(InsulatingOilKind.NotInformed, response.OilType.Kind);
        Assert.All(response.PhysicalChemical.Tests, test =>
        {
            Assert.Null(test.Limit);
            Assert.Equal(PhysicalChemicalLimitAbsence.OilTypeNotInformed, test.LimitAbsence);
        });
    }

    [Theory]
    [InlineData("Ascarel")]
    [InlineData("Éster sintético")]
    public void AnOilTypeThatWasInformedButNotRecognizedSaysSo(string reported)
    {
        HealthIndicatorsResponse response = Flatten(reported);

        Assert.Equal(InsulatingOilKind.Unrecognized, response.OilType.Kind);
        Assert.Equal(reported, response.OilType.Reported);
        Assert.All(response.PhysicalChemical.Tests, test =>
        {
            Assert.Null(test.Limit);
            Assert.Equal(PhysicalChemicalLimitAbsence.OilTypeUnrecognized, test.LimitAbsence);
        });
    }

    [Theory]
    [InlineData("Óleo Vegetal Isolante")]
    [InlineData("Silicone")]
    [InlineData(null)]
    public void ALimitPublishedByTheLaboratoryIsKeptAsItCame(string? reported)
    {
        HealthIndicatorsResponse response = Flatten(
            reported,
            ClientMeasurementMapper.Map(new ClientMeasurement(
                "rigidez-lab", "Rigidez dielétrica", "kV", "ABNT NBR IEC 60156", "min. 35",
                new ClientMeasurementResult.Quantity(52m), "rigidez")));

        PhysicalChemicalTestResponse rigidity = Assert.Single(response.PhysicalChemical.Tests);

        Assert.Equal("min. 35", rigidity.Limit);
        Assert.Null(rigidity.LimitAbsence);
    }

    private static HealthIndicatorsResponse Flatten(string? oilType)
    {
        return Flatten(
            oilType,
            AnalysisBuilder.MeasurementOf("Rigidez Dielétrica - Calota (kV)", "48"),
            AnalysisBuilder.MeasurementOf("Índice de Neutralização (mg KOH/g óleo)", "0,05"));
    }

    private static HealthIndicatorsResponse Flatten(string? oilType, params Measurement[] measurements)
    {
        SampleAnalysis analysis = AnalysisBuilder.Valid(measurements: measurements);

        return PositionalFlattening.Flatten(
            new SampleHealthIndicators(
                analysis.SerialNumber,
                analysis.Identification,
                AnalysisSeries.Build(analysis.EquipmentKey, [analysis]),
                AnalysisSeries.Build(analysis.EquipmentKey, []),
                null,
                [],
                [])
            {
                Oil = InsulatingOil.Recognize(oilType)
            },
            "opaque-identifier");
    }
}
