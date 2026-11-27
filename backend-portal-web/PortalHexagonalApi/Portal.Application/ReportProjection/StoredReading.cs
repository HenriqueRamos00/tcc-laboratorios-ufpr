using Portal.Application.ReportDocuments;

namespace Portal.Application.ReportProjection;

public sealed record ReadingControl(
    Guid SignedFileId,
    ReportReadOutcome Outcome,
    int ExtractorVersion,
    DateTime ReadAt,
    int Attempts);

public sealed record StoredReading(
    Guid SignedFileId,
    Guid ReportId,
    string Identification,
    string Cnpj,
    string? EquipmentKey,
    DateTime? IssueDate,
    ReportReadOutcome Outcome,
    int ExtractorVersion,
    DateTime ReadAt,
    int Attempts,
    ReportSection? Chromatography,
    ReportSection? PhysicalChemical)
{
    public ReadingControl Control => new(SignedFileId, Outcome, ExtractorVersion, ReadAt, Attempts);

    public ReportReading ToReading()
    {
        return new ReportReading(Identification, IssueDate, Chromatography, PhysicalChemical)
        {
            Origin = new SignedReportOrigin(Cnpj, ReportId, SignedFileId, Outcome)
        };
    }
}

public static class ReadingPolicy
{
    public const int MaxAttempts = 3;

    public static bool ShouldRead(ReadingControl? control, DateOnly today)
    {
        if (control is null) return true;

        if (control.ExtractorVersion < SignedReportFindings.ExtractorVersion) return true;

        return control.Outcome == ReportReadOutcome.Unreadable
            && control.Attempts < MaxAttempts
            && DateOnly.FromDateTime(control.ReadAt) < today;
    }

    public static int NextAttempt(ReadingControl? control)
    {
        return control is null || control.ExtractorVersion < SignedReportFindings.ExtractorVersion
            ? 1
            : control.Attempts + 1;
    }
}
