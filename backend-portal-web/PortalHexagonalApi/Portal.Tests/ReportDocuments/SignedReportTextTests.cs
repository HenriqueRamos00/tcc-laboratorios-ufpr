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
    public void AnUnreadableFileYieldsNothing()
    {
        Assert.Equal(SignedReportFindings.None, SignedReportText.Read([.. "isto não é um PDF"u8]));
        Assert.Equal(SignedReportFindings.None, SignedReportText.Read([]));
    }
}
