using Portal.Application.Visibility;

namespace Portal.Application.ReportProjection;

public sealed record ReportLoadResult(
    int ReportsRead,
    int ReportsUnreadable,
    int EquipmentUpdated,
    IReadOnlyList<string> Problems,
    bool AlreadyRunning = false)
{
    public static ReportLoadResult InProgress { get; } = new(0, 0, 0, [], AlreadyRunning: true);

    public bool Success => Problems.Count == 0;
}

public sealed record LoadFinishedNotice(string Status, DateTime At, int ReportsRead)
{
    public const string EventName = "carga.concluida";
    public const string Succeeded = "success";
    public const string Failed = "failure";
}

public sealed record ReportLoadAcceptance(int Accepted, bool AlreadyRunning)
{
    public static ReportLoadAcceptance Nothing { get; } = new(0, AlreadyRunning: false);
}

public interface IReportLoad
{
    bool IsAvailable { get; }

    Task<bool> IsDueAtStartupAsync(DateTime startOfTodayUtc, bool pastTheDailyTime, CancellationToken cancellationToken);

    Task<ReportLoadResult> RunDailyAsync(CancellationToken cancellationToken);

    Task<ReportLoadResult> RefreshEquipmentAsync(
        CompanyScope scope,
        string equipmentKey,
        string userKey,
        CancellationToken cancellationToken);

    Task<ReportLoadAcceptance> RequestEquipmentAsync(
        CompanyScope scope,
        IReadOnlyCollection<string> equipmentKeys,
        string userKey,
        CancellationToken cancellationToken);

    ReportLoadAcceptance RequestCompanies(CompanyScope scope, string userKey);

    Task<ReportLoadResult> ServeRequestsAsync(CancellationToken cancellationToken);
}

public sealed class ReportLoadOptions
{
    public const string SectionName = "ReportLoad";
    public const string DefaultTimeZone = "America/Sao_Paulo";
    public const int SlackDays = 1;

    public bool Enabled { get; set; } = true;

    public string DailyAt { get; set; } = "06:00";

    public string RetryAt { get; set; } = "15:00";

    public string TimeZone { get; set; } = DefaultTimeZone;

    public int PauseBetweenPdfsMilliseconds { get; set; } = 250;

    public int BatchSize { get; set; } = 100;

    public int MaxPdfsPerRun { get; set; } = 2000;

    public int MaxConsecutiveSourceFailures { get; set; } = 3;

    public int MaxEquipmentPerBatch { get; set; } = 100;

    public int MaxCompaniesPerRefresh { get; set; } = 20;

    public int MaxPdfsPerRefresh { get; set; } = 500;

    public TimeSpan PauseBetweenPdfs => TimeSpan.FromMilliseconds(PauseBetweenPdfsMilliseconds);

    public IEnumerable<string> Problems()
    {
        if (!TimeOnly.TryParse(DailyAt, out _)) yield return $"{SectionName}:DailyAt (HH:mm)";
        if (!TimeOnly.TryParse(RetryAt, out _)) yield return $"{SectionName}:RetryAt (HH:mm)";
        if (PauseBetweenPdfsMilliseconds is < 0 or > 60_000) yield return $"{SectionName}:PauseBetweenPdfsMilliseconds (0 a 60000)";
        if (BatchSize is < 1 or > 1000) yield return $"{SectionName}:BatchSize (1 a 1000)";
        if (MaxPdfsPerRun is < 1 or > 100_000) yield return $"{SectionName}:MaxPdfsPerRun (1 a 100000)";
        if (MaxConsecutiveSourceFailures is < 1 or > 100) yield return $"{SectionName}:MaxConsecutiveSourceFailures (1 a 100)";
        if (MaxEquipmentPerBatch is < 1 or > 200) yield return $"{SectionName}:MaxEquipmentPerBatch (1 a 200)";
        if (MaxCompaniesPerRefresh is < 1 or > 1000) yield return $"{SectionName}:MaxCompaniesPerRefresh (1 a 1000)";
        if (MaxPdfsPerRefresh is < 1 or > 100_000) yield return $"{SectionName}:MaxPdfsPerRefresh (1 a 100000)";
    }
}
