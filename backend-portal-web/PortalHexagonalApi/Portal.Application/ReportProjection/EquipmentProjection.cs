using Portal.Application.ReportDocuments;
using Portal.Application.ReportProjection.Exceptions;
using Portal.Application.Visibility;

namespace Portal.Application.ReportProjection;

public sealed record ProjectionReading(
    IReadOnlyList<ReportReading> Readings,
    int ReportsRead,
    int ReportsUnreadable,
    bool Projected);

public sealed class EquipmentProjection
{
    private readonly IReportProjectionStore _store;
    private readonly IReportIssueRepository _reportIssues;
    private readonly TimeProvider _time;

    public EquipmentProjection(
        IReportProjectionStore store,
        IReportIssueRepository reportIssues,
        TimeProvider time)
    {
        _store = store;
        _reportIssues = reportIssues;
        _time = time;
    }

    public async Task<ProjectionReading> ReadAsync(
        CompanyScope scope,
        ProjectedEquipment equipment,
        IReadOnlyList<string> identifications,
        TimeSpan pauseBetweenReports,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(scope);
        ArgumentNullException.ThrowIfNull(equipment);
        ArgumentNullException.ThrowIfNull(identifications);

        string[] wanted = [.. identifications
            .Where(identification => !string.IsNullOrWhiteSpace(identification))
            .Select(identification => identification.Trim())
            .Distinct(StringComparer.OrdinalIgnoreCase)];

        DateTime now = _time.GetUtcNow().UtcDateTime;
        DateOnly today = DateOnly.FromDateTime(now);

        Dictionary<string, StoredReading>? known = await KnownAsync(scope, wanted, cancellationToken);

        string[] missing = [.. wanted.Where(identification => known is null
            || !known.TryGetValue(identification, out StoredReading? stored)
            || ReadingPolicy.ShouldRead(stored.Control, today))];

        List<ReportReading> fresh = await ReadFreshAsync(scope, missing, pauseBetweenReports, cancellationToken);

        bool projected = known is not null
            && await RecordAsync(scope, equipment, known, fresh, now, cancellationToken);

        return new ProjectionReading(
            [.. wanted
                .Select(identification => fresh.FirstOrDefault(reading => Same(reading.Identification, identification))
                    ?? known?.GetValueOrDefault(identification)?.ToReading())
                .OfType<ReportReading>()],
            fresh.Count(reading => reading.Origin is not null),
            fresh.Count(reading => reading.Origin?.Outcome == ReportReadOutcome.Unreadable),
            projected);
    }

    private async Task<Dictionary<string, StoredReading>?> KnownAsync(
        CompanyScope scope,
        string[] wanted,
        CancellationToken cancellationToken)
    {
        if (!_store.IsConnected) return null;

        try
        {
            IReadOnlyList<StoredReading> stored = wanted.Length == 0
                ? []
                : await _store.GetReadingsAsync(scope.Cnpjs, wanted, cancellationToken);

            return stored
                .GroupBy(reading => reading.Identification, StringComparer.OrdinalIgnoreCase)
                .ToDictionary(
                    ofReport => ofReport.Key,
                    ofReport => ofReport
                        .OrderByDescending(reading => reading.IssueDate)
                        .ThenByDescending(reading => reading.ReadAt)
                        .First(),
                    StringComparer.OrdinalIgnoreCase);
        }
        catch (ReportProjectionUnavailableException)
        {
            return null;
        }
    }

    private async Task<List<ReportReading>> ReadFreshAsync(
        CompanyScope scope,
        string[] missing,
        TimeSpan pauseBetweenReports,
        CancellationToken cancellationToken)
    {
        if (missing.Length == 0) return [];

        if (pauseBetweenReports <= TimeSpan.Zero)
            return [.. await _reportIssues.ReadReportsAsync(scope, missing, cancellationToken)];

        List<ReportReading> readings = [];

        foreach (string identification in missing)
        {
            cancellationToken.ThrowIfCancellationRequested();

            IReadOnlyList<ReportReading> read = await _reportIssues
                .ReadReportsAsync(scope, [identification], cancellationToken);

            readings.AddRange(read);

            if (read.Any(reading => reading.Origin is not null))
                await Task.Delay(pauseBetweenReports, _time, cancellationToken);
        }

        return readings;
    }

    private async Task<bool> RecordAsync(
        CompanyScope scope,
        ProjectedEquipment equipment,
        Dictionary<string, StoredReading> known,
        List<ReportReading> fresh,
        DateTime now,
        CancellationToken cancellationToken)
    {
        string? onlyCnpj = scope.Cnpjs.Count == 1 ? scope.Cnpjs[0] : null;

        List<(ReportReading Reading, SignedReportOrigin Origin, string Cnpj)> signed = [.. fresh
            .Where(reading => reading.Origin is not null && (reading.Origin.Cnpj ?? onlyCnpj) is not null)
            .Select(reading => (reading, reading.Origin!, (reading.Origin!.Cnpj ?? onlyCnpj)!))];

        string? cnpj = onlyCnpj
            ?? signed.Select(read => read.Cnpj).FirstOrDefault()
            ?? known.Values.Select(stored => stored.Cnpj).FirstOrDefault();

        try
        {
            Dictionary<Guid, ReadingControl> controls = signed.Count == 0
                ? []
                : (await _store.GetControlsAsync(
                        [.. signed.Select(read => read.Origin.SignedFileId).Distinct()], cancellationToken))
                    .ToDictionary(control => control.SignedFileId);

            foreach ((ReportReading reading, SignedReportOrigin origin, string owner) in signed)
            {
                await _store.SaveReadingAsync(
                    new StoredReading(
                        origin.SignedFileId,
                        origin.ReportId,
                        reading.Identification,
                        owner,
                        equipment.EquipmentKey,
                        reading.IssueDate,
                        origin.Outcome,
                        SignedReportFindings.ExtractorVersion,
                        now,
                        ReadingPolicy.NextAttempt(controls.GetValueOrDefault(origin.SignedFileId)),
                        reading.Chromatography,
                        reading.PhysicalChemical),
                    cancellationToken);
            }

            if (cnpj is null) return false;

            IReadOnlyList<ReadSummary> summaries = await _store
                .GetReadSummariesAsync(cnpj, equipment.Identifications, cancellationToken);

            await _store.SaveStatusesAsync(
                cnpj,
                [EquipmentStatusRule.Of(cnpj, equipment, summaries, now)],
                cancellationToken);

            return true;
        }
        catch (ReportProjectionUnavailableException)
        {
            return false;
        }
    }

    private static bool Same(string left, string right)
    {
        return string.Equals(left, right, StringComparison.OrdinalIgnoreCase);
    }
}
