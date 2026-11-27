using Portal.Adapters.ReportDocuments;
using Portal.Application.ReportDocuments;
using Portal.Application.ReportProjection;

using Xunit;

namespace Portal.Tests.ReportProjection;

public sealed class ReadingRulesTests
{
    private static readonly DateTime ReadAt = new(2026, 11, 10, 9, 0, 0, DateTimeKind.Utc);
    private static readonly DateOnly SameDay = new(2026, 11, 10);
    private static readonly DateOnly NextDay = new(2026, 11, 11);

    [Fact]
    public void AFileNeverReadIsRead()
    {
        Assert.True(ReadingPolicy.ShouldRead(null, SameDay));
        Assert.Equal(1, ReadingPolicy.NextAttempt(null));
    }

    [Theory]
    [InlineData(ReportReadOutcome.Extracted)]
    [InlineData(ReportReadOutcome.NoSections)]
    public void AFileAlreadyReadIsNotReadAgainEvenWhenNoSectionWasFound(ReportReadOutcome outcome)
    {
        ReadingControl control = Control(outcome, attempts: 1);

        Assert.False(ReadingPolicy.ShouldRead(control, SameDay));
        Assert.False(ReadingPolicy.ShouldRead(control, NextDay));
        Assert.False(ReadingPolicy.ShouldRead(control, NextDay.AddDays(400)));
    }

    [Fact]
    public void AnUnreadableFileIsTriedAgainOnlyOnAnotherDay()
    {
        ReadingControl control = Control(ReportReadOutcome.Unreadable, attempts: 1);

        Assert.False(ReadingPolicy.ShouldRead(control, SameDay));
        Assert.True(ReadingPolicy.ShouldRead(control, NextDay));
        Assert.Equal(2, ReadingPolicy.NextAttempt(control));
    }

    [Fact]
    public void AnUnreadableFileStopsBeingTriedAfterTheThirdAttempt()
    {
        Assert.True(ReadingPolicy.ShouldRead(Control(ReportReadOutcome.Unreadable, attempts: 2), NextDay));
        Assert.False(ReadingPolicy.ShouldRead(Control(ReportReadOutcome.Unreadable, attempts: 3), NextDay));
        Assert.False(ReadingPolicy.ShouldRead(Control(ReportReadOutcome.Unreadable, attempts: 3), NextDay.AddDays(30)));
    }

    [Fact]
    public void AFileReadByAnOlderExtractorIsReadAgainFromTheFirstAttempt()
    {
        ReadingControl old = new(
            Guid.NewGuid(), ReportReadOutcome.Extracted, SignedReportFindings.ExtractorVersion - 1, ReadAt, 3);

        Assert.True(ReadingPolicy.ShouldRead(old, SameDay));
        Assert.Equal(1, ReadingPolicy.NextAttempt(old));
    }

    [Theory]
    [InlineData(0.0, 1.0, 0.0, Conformity.Normal)]
    [InlineData(0.776, 0.937, 0.808, Conformity.Normal)]
    [InlineData(0.506, 0.780, 0.518, Conformity.Normal)]
    [InlineData(1.0, 1.0, 0.0, Conformity.Warning)]
    [InlineData(1.0, 0.757, 0.027, Conformity.Warning)]
    [InlineData(1.0, 0.922, 0.612, Conformity.Warning)]
    [InlineData(1.0, 0.0, 0.0, Conformity.NonConforming)]
    [InlineData(1.0, 0.780, 0.808, Conformity.NonConforming)]
    [InlineData(0.898, 0.451, 0.451, Conformity.NonConforming)]
    public void GreenYellowAndRedAreRecognizedInStrongAndPaleShades(
        double red, double green, double blue, Conformity expected)
    {
        Assert.Equal(expected, ResultColor.ConformityOf((red, green, blue)));
    }

    [Theory]
    [InlineData(1.0, 1.0, 1.0)]
    [InlineData(0.85, 0.85, 0.85)]
    [InlineData(0.0, 0.0, 0.0)]
    [InlineData(0.0, 0.0, 1.0)]
    [InlineData(0.39, 0.71, 0.96)]
    [InlineData(0.6, 0.2, 0.8)]
    public void WhiteGrayBlackAndOtherHuesAreNotAClassification(double red, double green, double blue)
    {
        Assert.Null(ResultColor.ConformityOf((red, green, blue)));
    }

    [Fact]
    public void TheWorstSectionDecidesTheOverallStatus()
    {
        Assert.Equal(OverallStatus.NonConforming, Overall(Conformity.Normal, Conformity.NonConforming));
        Assert.Equal(OverallStatus.NonConforming, Overall(Conformity.NonConforming, Conformity.Warning));
        Assert.Equal(OverallStatus.Warning, Overall(Conformity.Warning, Conformity.Normal));
        Assert.Equal(OverallStatus.Normal, Overall(Conformity.Normal, Conformity.Normal));
        Assert.Equal(OverallStatus.Normal, Overall(Conformity.Normal, null));
        Assert.Equal(OverallStatus.Unclassified, Overall(null, null));
    }

    [Fact]
    public void EachSectionComesFromTheMostRecentReportThatHasIt()
    {
        EquipmentStatus status = EquipmentStatusRule.Of(
            "30960572000183",
            Equipment("R-1", "R-2", "R-3"),
            [
                new ReadSummary("R-1", new DateTime(2026, 1, 10), Section(Conformity.NonConforming, "Arco"), Section(Conformity.Warning, "Atenção")),
                new ReadSummary("R-2", new DateTime(2026, 5, 10), Section(Conformity.Normal, "NORMAL"), null),
                new ReadSummary("R-3", new DateTime(2026, 9, 10), null, null),
                new ReadSummary("R-9", new DateTime(2026, 10, 10), Section(Conformity.NonConforming, "De outro equipamento"), null)
            ],
            ReadAt);

        Assert.Equal(new DateTime(2026, 5, 10), status.LastReportDate);
        Assert.Equal(new SectionStatus(Conformity.Normal, "NORMAL"), status.Chromatography);
        Assert.Equal(new SectionStatus(Conformity.Warning, "Atenção"), status.PhysicalChemical);
        Assert.Equal(OverallStatus.Warning, status.Overall);
    }

    [Fact]
    public void AnEquipmentWithoutAnyReadReportIsToldApartFromOneWithoutClassification()
    {
        EquipmentStatus without = EquipmentStatusRule.Of("30960572000183", Equipment("R-1"), [], ReadAt);
        EquipmentStatus unclassified = EquipmentStatusRule.Of(
            "30960572000183",
            Equipment("R-1"),
            [new ReadSummary("r-1", new DateTime(2026, 9, 10), Section(null, "Rótulo de cor desconhecida"), null)],
            ReadAt);

        Assert.Equal(OverallStatus.NoReport, without.Overall);
        Assert.Null(without.LastReportDate);
        Assert.Equal(OverallStatus.Unclassified, unclassified.Overall);
        Assert.Equal("Rótulo de cor desconhecida", unclassified.Chromatography?.ResultLabel);
    }

    [Fact]
    public void AReportWithoutSectionsNeverDecidesTheStatus()
    {
        EquipmentStatus status = EquipmentStatusRule.Of(
            "30960572000183",
            Equipment("R-1", "R-2"),
            [
                new ReadSummary("R-2", new DateTime(2026, 11, 20), null, null),
                new ReadSummary("R-1", new DateTime(2026, 11, 12), Section(Conformity.Normal, "NORMAL"), Section(Conformity.Warning, "Atenção"))
            ],
            ReadAt);

        Assert.Equal(OverallStatus.Warning, status.Overall);
        Assert.Equal(new DateTime(2026, 11, 12), status.LastReportDate);
        Assert.Equal(new SectionStatus(Conformity.Normal, "NORMAL"), status.Chromatography);
    }

    [Fact]
    public void AnEquipmentWhoseReportsHaveNoSectionsIsWithoutReport()
    {
        EquipmentStatus status = EquipmentStatusRule.Of(
            "30960572000183",
            Equipment("R-1", "R-2"),
            [
                new ReadSummary("R-1", new DateTime(2026, 11, 12), null, null),
                new ReadSummary("R-2", new DateTime(2026, 11, 20), null, null)
            ],
            ReadAt);

        Assert.Equal(OverallStatus.NoReport, status.Overall);
        Assert.Null(status.LastReportDate);
        Assert.Null(status.Chromatography);
        Assert.Null(status.PhysicalChemical);
    }

    private static OverallStatus Overall(Conformity? chromatography, Conformity? physicalChemical)
    {
        return EquipmentStatusRule.Of(
            "30960572000183",
            Equipment("R-1"),
            [new ReadSummary(
                "R-1",
                new DateTime(2026, 9, 10),
                Section(chromatography, "a"),
                Section(physicalChemical, "b"))],
            ReadAt).Overall;
    }

    private static SectionStatus Section(Conformity? conformity, string label) => new(conformity, label);

    private static ProjectedEquipment Equipment(params string[] identifications)
    {
        return new ProjectedEquipment(
            "571289", "571289", "571289", null, null, false, "EMPRESA DE ENERGIA",
            identifications[0], identifications.Length, new DateTime(2026, 9, 1), identifications);
    }

    private static ReadingControl Control(ReportReadOutcome outcome, int attempts)
    {
        return new ReadingControl(Guid.NewGuid(), outcome, SignedReportFindings.ExtractorVersion, ReadAt, attempts);
    }
}
