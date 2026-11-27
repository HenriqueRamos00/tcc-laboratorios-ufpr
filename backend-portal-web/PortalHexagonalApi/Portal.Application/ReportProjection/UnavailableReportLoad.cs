using Portal.Application.Visibility;

namespace Portal.Application.ReportProjection;

public sealed class UnavailableReportLoad : IReportLoad
{
    private static readonly ReportLoadResult NothingDone = new(0, 0, 0, []);

    public bool IsAvailable => false;

    public Task<bool> IsDueAtStartupAsync(
        DateTime startOfTodayUtc,
        bool pastTheDailyTime,
        CancellationToken cancellationToken) =>
        Task.FromResult(false);

    public Task<ReportLoadResult> RunDailyAsync(CancellationToken cancellationToken) =>
        Task.FromResult(NothingDone);

    public Task<ReportLoadResult> RefreshEquipmentAsync(
        CompanyScope scope,
        string equipmentKey,
        string userKey,
        CancellationToken cancellationToken) =>
        Task.FromResult(NothingDone);

    public Task<ReportLoadAcceptance> RequestEquipmentAsync(
        CompanyScope scope,
        IReadOnlyCollection<string> equipmentKeys,
        string userKey,
        CancellationToken cancellationToken) =>
        Task.FromResult(ReportLoadAcceptance.Nothing);

    public ReportLoadAcceptance RequestCompanies(CompanyScope scope, string userKey) =>
        ReportLoadAcceptance.Nothing;

    public Task<ReportLoadResult> ServeRequestsAsync(CancellationToken cancellationToken) =>
        Task.FromResult(NothingDone);
}
