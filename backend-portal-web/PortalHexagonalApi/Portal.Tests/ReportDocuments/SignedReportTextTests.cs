using Portal.Adapters.ReportDocuments;
using Portal.Application.ReportDocuments;

using Xunit;

namespace Portal.Tests.ReportDocuments;

public sealed class SignedReportTextTests
{
    [Fact]
    public void BothSectionsComeWithTheirLabelTitleAndParagraphs()
    {
        SignedReportFindings findings = SignedReportText.Read(SyntheticReportPdf.Of("NORMAL", "NORMAL"));

        ReportSection gas = Assert.IsType<ReportSection>(findings.Chromatography);
        ReportSection physicalChemical = Assert.IsType<ReportSection>(findings.PhysicalChemical);

        Assert.Equal("NORMAL", gas.ResultLabel);
        Assert.Equal(SyntheticReportPdf.GasConclusionTitle, gas.Title);
        Assert.Equal([SyntheticReportPdf.GasParagraph, SyntheticReportPdf.NewAnalysis], gas.Paragraphs);
        Assert.Equal("NORMAL", physicalChemical.ResultLabel);
        Assert.Equal(SyntheticReportPdf.PhysicalChemicalConclusionTitle, physicalChemical.Title);
        Assert.Equal(
            [SyntheticReportPdf.PhysicalChemicalParagraph, SyntheticReportPdf.NewAnalysis],
            physicalChemical.Paragraphs);
    }

    [Fact]
    public void AReportWithOnlyThePhysicalChemicalTableHasNoChromatography()
    {
        SignedReportFindings findings = SignedReportText.Read(SyntheticReportPdf.Of(null, "NORMAL"));

        Assert.Null(findings.Chromatography);
        Assert.Equal("NORMAL", findings.PhysicalChemical?.ResultLabel);
        Assert.Equal(SyntheticReportPdf.PhysicalChemicalConclusionTitle, findings.PhysicalChemical?.Title);
        Assert.Equal(2, findings.PhysicalChemical?.Paragraphs.Count);
    }

    [Fact]
    public void ALabelOfTwoWordsIsReadWhole()
    {
        SignedReportFindings findings = SignedReportText.Read(SyntheticReportPdf.Of("Descarga elétrica", "NORMAL"));

        Assert.Equal("Descarga elétrica", findings.Chromatography?.ResultLabel);
        Assert.Equal("NORMAL", findings.PhysicalChemical?.ResultLabel);
    }

    [Fact]
    public void ALabelThatWrapsInsideTheCellIsReadWhole()
    {
        SignedReportFindings findings = SignedReportText.Read(
            SyntheticReportPdf.Of("Superaquecimento de alta temperatura", null));

        Assert.Equal("Superaquecimento de alta temperatura", findings.Chromatography?.ResultLabel);
    }

    [Fact]
    public void AParagraphThatWrapsComesBackAsOneParagraph()
    {
        SignedReportFindings findings = SignedReportText.Read(SyntheticReportPdf.Of(
            "Descarga elétrica",
            null,
            gasParagraphs: [SyntheticReportPdf.LongParagraph, SyntheticReportPdf.NewAnalysis]));

        Assert.Equal(
            [SyntheticReportPdf.LongParagraph, SyntheticReportPdf.NewAnalysis],
            findings.Chromatography?.Paragraphs);
    }

    [Fact]
    public void WithoutTheConclusionBlocksOnlyTheLabelsAreRead()
    {
        SignedReportFindings findings = SignedReportText.Read(
            SyntheticReportPdf.Of("NORMAL", "NORMAL", withConclusions: false));

        Assert.Equal("NORMAL", findings.Chromatography?.ResultLabel);
        Assert.Null(findings.Chromatography?.Title);
        Assert.Empty(findings.Chromatography!.Paragraphs);
        Assert.Equal("NORMAL", findings.PhysicalChemical?.ResultLabel);
        Assert.Null(findings.PhysicalChemical?.Title);
    }

    [Fact]
    public void AnUnreadableFileIsToldApartFromAFileWithoutSections()
    {
        Assert.Equal(ReportReadOutcome.Unreadable, SignedReportText.Read([.. "isto não é um PDF"u8]).Outcome);
        Assert.Equal(ReportReadOutcome.Unreadable, SignedReportText.Read([]).Outcome);
        Assert.Equal(ReportReadOutcome.NoSections, SignedReportText.Read(SyntheticReportPdf.Of(null, null)).Outcome);
        Assert.Equal(ReportReadOutcome.Extracted, SignedReportText.Read(SyntheticReportPdf.Of("NORMAL", null)).Outcome);
    }

    [Theory]
    [InlineData(CellFill.Green, Conformity.Normal)]
    [InlineData(CellFill.Yellow, Conformity.Warning)]
    [InlineData(CellFill.Red, Conformity.NonConforming)]
    public void TheFillOfTheResultCellDecidesTheConformityOfEachSection(CellFill fill, Conformity conformity)
    {
        SignedReportFindings findings = SignedReportText.Read(SyntheticReportPdf.Of(
            "Rótulo qualquer", "Outro rótulo", gasFill: fill, physicalChemicalFill: fill));

        Assert.Equal(conformity, findings.Chromatography?.Conformity);
        Assert.Equal(conformity, findings.PhysicalChemical?.Conformity);
        Assert.Equal("Rótulo qualquer", findings.Chromatography?.ResultLabel);
        Assert.Equal("Outro rótulo", findings.PhysicalChemical?.ResultLabel);
    }

    [Fact]
    public void EachSectionIsClassifiedByItsOwnCell()
    {
        SignedReportFindings findings = SignedReportText.Read(SyntheticReportPdf.Of(
            "NORMAL", "NORMAL", gasFill: CellFill.Yellow, physicalChemicalFill: CellFill.Red));

        Assert.Equal(Conformity.Warning, findings.Chromatography?.Conformity);
        Assert.Equal(Conformity.NonConforming, findings.PhysicalChemical?.Conformity);
        Assert.Equal("#FFF176", findings.Chromatography?.ResultColor);
        Assert.Equal("#E57373", findings.PhysicalChemical?.ResultColor);
    }

    [Theory]
    [InlineData(CellFill.Gray)]
    [InlineData(CellFill.Blue)]
    [InlineData(CellFill.None)]
    public void AnUnrecognizedFillKeepsTheLabelAndLeavesTheSectionUnclassified(CellFill fill)
    {
        SignedReportFindings findings = SignedReportText.Read(SyntheticReportPdf.Of(
            "NORMAL", null, gasFill: fill));

        Assert.Equal("NORMAL", findings.Chromatography?.ResultLabel);
        Assert.Null(findings.Chromatography?.Conformity);
    }

    [Fact]
    public void ALabelThatWrapsIsStillClassifiedByTheCellBehindIt()
    {
        SignedReportFindings findings = SignedReportText.Read(SyntheticReportPdf.Of(
            "Superaquecimento de alta temperatura", null, gasFill: CellFill.Yellow));

        Assert.Equal("Superaquecimento de alta temperatura", findings.Chromatography?.ResultLabel);
        Assert.Equal(Conformity.Warning, findings.Chromatography?.Conformity);
    }
}
