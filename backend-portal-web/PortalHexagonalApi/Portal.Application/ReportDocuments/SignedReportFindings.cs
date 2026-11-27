namespace Portal.Application.ReportDocuments;

public sealed record SignedReportFindings(ReportSection? Chromatography, ReportSection? PhysicalChemical)
{
    public const int ExtractorVersion = 2;

    public static readonly SignedReportFindings None = new(null, null);

    public static readonly SignedReportFindings Unreadable = new(null, null) { Readable = false };

    public bool Readable { get; init; } = true;

    public ReportReadOutcome Outcome => !Readable
        ? ReportReadOutcome.Unreadable
        : Chromatography is null && PhysicalChemical is null
            ? ReportReadOutcome.NoSections
            : ReportReadOutcome.Extracted;
}
