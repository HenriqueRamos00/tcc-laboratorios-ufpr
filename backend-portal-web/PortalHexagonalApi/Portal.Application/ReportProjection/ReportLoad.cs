using System.Text.Json;

using Portal.Application.LabResults;
using Portal.Application.LabResults.Exceptions;
using Portal.Application.Nameplate;
using Portal.Application.Nameplate.Exceptions;
using Portal.Application.Paging;
using Portal.Application.RealTime;
using Portal.Application.ReportDocuments;
using Portal.Application.ReportDocuments.Exceptions;
using Portal.Application.ReportProjection.Exceptions;
using Portal.Application.Visibility;
using Portal.Domain.LabResults;
using Portal.Domain.Nameplate;

namespace Portal.Application.ReportProjection;

public sealed class ReportLoad : IReportLoad
{
    private static readonly PageRequest MostRecentAnalyses = new(1, LabResultsConsultation.MaxAnalysesPerEquipment);

    private readonly ReportLoadInProgress _inProgress;
    private readonly IReportProjectionStore _store;
    private readonly ISignedReportSource _source;
    private readonly ILabTestRepository _labTests;
    private readonly IEquipmentNameplateRepository _nameplates;
    private readonly EquipmentProjection _projection;
    private readonly IRealTimeChannel _channel;
    private readonly ReportLoadOptions _options;
    private readonly TimeProvider _time;

    public ReportLoad(
        ReportLoadInProgress inProgress,
        IReportProjectionStore store,
        ISignedReportSource source,
        ILabTestRepository labTests,
        IEquipmentNameplateRepository nameplates,
        EquipmentProjection projection,
        IRealTimeChannel channel,
        ReportLoadOptions options,
        TimeProvider time)
    {
        _inProgress = inProgress;
        _store = store;
        _source = source;
        _labTests = labTests;
        _nameplates = nameplates;
        _projection = projection;
        _channel = channel;
        _options = options;
        _time = time;
    }

    public bool IsAvailable => _store.IsConnected;

    public async Task<bool> IsDueAtStartupAsync(
        DateTime startOfTodayUtc,
        bool pastTheDailyTime,
        CancellationToken cancellationToken)
    {
        IReadOnlyList<LoadScope> scopes = await _store.GetActiveScopesAsync(cancellationToken);

        if (scopes.Count == 0) return false;

        if (scopes.Any(scope => scope.LastRunAt is null)) return true;

        return pastTheDailyTime && !scopes.Any(scope => scope.LastRunAt >= startOfTodayUtc);
    }

    public Task<ReportLoadResult> RunDailyAsync(CancellationToken cancellationToken)
    {
        return RunAsync(daily: true, cancellationToken);
    }

    public Task<ReportLoadResult> RefreshEquipmentAsync(
        CompanyScope scope,
        string equipmentKey,
        string userKey,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(scope);
        ArgumentException.ThrowIfNullOrWhiteSpace(equipmentKey);

        _inProgress.RegisterInterest(userKey, new EquipmentRefreshRequest(scope, equipmentKey.Trim()));

        return RunAsync(daily: false, cancellationToken);
    }

    public async Task<ReportLoadAcceptance> RequestEquipmentAsync(
        CompanyScope scope,
        IReadOnlyCollection<string> equipmentKeys,
        string userKey,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(scope);
        ArgumentNullException.ThrowIfNull(equipmentKeys);

        string[] wanted = [.. equipmentKeys
            .Where(key => !string.IsNullOrWhiteSpace(key))
            .Select(key => key.Trim())
            .Distinct(StringComparer.Ordinal)];

        if (scope.IsEmpty || wanted.Length == 0 || string.IsNullOrWhiteSpace(userKey))
            return ReportLoadAcceptance.Nothing;

        HashSet<string> known = await KnownKeysAsync(scope, wanted, cancellationToken);

        EquipmentRefreshRequest[] missing = [.. wanted
            .Where(key => !known.Contains(key))
            .Select(key => new EquipmentRefreshRequest(scope, key, OnlyIfMissing: true))];

        if (missing.Length == 0) return ReportLoadAcceptance.Nothing;

        bool alreadyRunning = _inProgress.IsRunning;

        _inProgress.Enqueue(userKey, missing, []);

        return new ReportLoadAcceptance(missing.Length, alreadyRunning);
    }

    public ReportLoadAcceptance RequestCompanies(CompanyScope scope, string userKey)
    {
        ArgumentNullException.ThrowIfNull(scope);

        if (scope.IsEmpty || string.IsNullOrWhiteSpace(userKey)) return ReportLoadAcceptance.Nothing;

        string[] companies = [.. scope.Cnpjs.Take(_options.MaxCompaniesPerRefresh)];
        bool alreadyRunning = _inProgress.IsRunning;

        _inProgress.Enqueue(userKey, [], companies);

        return new ReportLoadAcceptance(companies.Length, alreadyRunning);
    }

    public Task<ReportLoadResult> ServeRequestsAsync(CancellationToken cancellationToken)
    {
        return RunAsync(daily: false, cancellationToken);
    }

    private async Task<HashSet<string>> KnownKeysAsync(
        CompanyScope scope,
        string[] equipmentKeys,
        CancellationToken cancellationToken)
    {
        try
        {
            return (await _store.GetStatusesAsync(scope.Cnpjs, equipmentKeys, cancellationToken))
                .Select(status => status.Equipment.EquipmentKey)
                .ToHashSet(StringComparer.Ordinal);
        }
        catch (ReportProjectionUnavailableException)
        {
            return new HashSet<string>(StringComparer.Ordinal);
        }
    }

    private async Task<ReportLoadResult> RunAsync(bool daily, CancellationToken cancellationToken)
    {
        if (!_inProgress.TryStart()) return ReportLoadResult.InProgress;

        Tally tally = new();
        bool finished = false;

        try
        {
            if (daily) await LoadActiveCompaniesAsync(tally, cancellationToken);

            await ServeEverythingAsync(tally, cancellationToken);

            finished = true;

            return tally.ToResult();
        }
        finally
        {
            _inProgress.Finish();
            NotifyInterested(tally, finished);
        }
    }

    private void NotifyInterested(Tally tally, bool finished)
    {
        IReadOnlySet<string> interested = _inProgress.HarvestInterested();

        if (interested.Count == 0) return;

        LoadFinishedNotice notice = new(
            finished && tally.Problems.Count == 0 ? LoadFinishedNotice.Succeeded : LoadFinishedNotice.Failed,
            _time.GetUtcNow().UtcDateTime,
            tally.ReportsRead);

        _channel.Publish(
            LoadFinishedNotice.EventName,
            JsonSerializer.Serialize(notice, JsonSerializerOptions.Web),
            interested);
    }

    private async Task ServeEverythingAsync(Tally tally, CancellationToken cancellationToken)
    {
        Budget budget = new(_options.MaxPdfsPerRefresh);

        while (true)
        {
            IReadOnlyList<string> companies = _inProgress.TakeCompanies();

            foreach (string cnpj in companies)
            {
                cancellationToken.ThrowIfCancellationRequested();

                try
                {
                    await LoadCompanyAsync(await ScopeOfAsync(cnpj, cancellationToken), budget, tally, cancellationToken);
                }
                catch (Exception failure) when (IsSourceFailure(failure))
                {
                    tally.Problems.Add(failure.Message);
                }
            }

            bool served = await ServeEquipmentAsync(tally, cancellationToken);

            if (!served && companies.Count == 0) return;
        }
    }

    private async Task<LoadScope> ScopeOfAsync(string cnpj, CancellationToken cancellationToken)
    {
        return (await _store.GetActiveScopesAsync(cancellationToken))
            .FirstOrDefault(scope => scope.Cnpj == cnpj)
            ?? new LoadScope(cnpj, Active: false, null, null, null);
    }

    private async Task<bool> ServeEquipmentAsync(Tally tally, CancellationToken cancellationToken)
    {
        bool served = false;

        while (true)
        {
            IReadOnlyList<EquipmentRefreshRequest> requests = _inProgress.TakeRequests();

            if (requests.Count == 0) return served;

            served = true;

            foreach (EquipmentRefreshRequest request in requests)
            {
                cancellationToken.ThrowIfCancellationRequested();

                try
                {
                    await RefreshOneAsync(request, tally, cancellationToken);
                }
                catch (Exception failure) when (IsSourceFailure(failure))
                {
                    tally.Problems.Add(failure.Message);
                }
            }
        }
    }

    private async Task RefreshOneAsync(
        EquipmentRefreshRequest request,
        Tally tally,
        CancellationToken cancellationToken)
    {
        if (request.OnlyIfMissing
            && (await _store.GetStatusesAsync(request.Scope.Cnpjs, [request.EquipmentKey], cancellationToken)).Count > 0)
            return;

        foreach (string cnpj in request.Scope.Cnpjs)
        {
            CompanyScope company = CompanyScope.OfCnpjs([cnpj]);

            Page<SampleAnalysis> analyses = await _labTests.GetAnalysesOfEquipmentAsync(
                company, request.EquipmentKey, MostRecentAnalyses, cancellationToken);

            if (analyses.Items.Count == 0) continue;

            TestedEquipment equipment = TestedEquipment.Gather(analyses.Items)[0];

            EquipmentNameplate? nameplate = equipment.SerialNumber is null
                ? null
                : await _nameplates.GetNameplateOfAsync(equipment.SerialNumber, cancellationToken);

            IReadOnlyList<ProtocolReport> reports = await _labTests.GetReportsOfProtocolsAsync(
                company,
                [.. equipment.Reports.Select(report => report.ProtocoloLactec)],
                cancellationToken);

            ProjectedEquipment projected = ProjectedEquipment.From(
                equipment, nameplate, analyses.TotalCount, reports);

            ProjectionReading reading = await _projection.ReadAsync(
                company,
                projected,
                [.. projected.Identifications.Take(LabResultsConsultation.MaxReportsRead)],
                _options.PauseBetweenPdfs,
                cancellationToken);

            tally.ReportsRead += reading.ReportsRead;
            tally.ReportsUnreadable += reading.ReportsUnreadable;

            if (reading.Projected)
                tally.EquipmentUpdated++;
            else
                tally.Problems.Add("A projeção local dos laudos não pôde ser gravada.");
        }
    }

    private async Task LoadActiveCompaniesAsync(Tally tally, CancellationToken cancellationToken)
    {
        IReadOnlyList<LoadScope> scopes;

        try
        {
            scopes = await _store.GetActiveScopesAsync(cancellationToken);
        }
        catch (ReportProjectionUnavailableException failure)
        {
            tally.Problems.Add(failure.Message);
            return;
        }

        Budget budget = new(_options.MaxPdfsPerRun);

        foreach (LoadScope scope in scopes)
        {
            cancellationToken.ThrowIfCancellationRequested();

            try
            {
                await LoadCompanyAsync(scope, budget, tally, cancellationToken);
            }
            catch (Exception failure) when (IsSourceFailure(failure))
            {
                tally.Problems.Add(failure.Message);
            }
        }
    }

    private async Task LoadCompanyAsync(
        LoadScope scope,
        Budget budget,
        Tally tally,
        CancellationToken cancellationToken)
    {
        IReadOnlyList<CompanyProtocol> protocols = await _source
            .GetProtocolsOfCompanyAsync(scope.Cnpj, cancellationToken);

        IReadOnlyList<TestedEquipment> equipmentList = TestedEquipment.Gather(protocols.Select(AnalysisOf));

        ILookup<string, ProtocolReport> reportsOfProtocol = protocols
            .SelectMany(protocol => protocol.Reports)
            .ToLookup(report => report.ProtocoloLactec, StringComparer.Ordinal);

        Dictionary<string, List<ProtocolReport>> reportsOfEquipment = equipmentList.ToDictionary(
            equipment => equipment.EquipmentKey,
            equipment => equipment.Reports
                .SelectMany(report => reportsOfProtocol[report.ProtocoloLactec])
                .ToList(),
            StringComparer.Ordinal);

        Dictionary<string, string> equipmentOfReport = new(StringComparer.OrdinalIgnoreCase);

        foreach (TestedEquipment equipment in equipmentList)
        {
            foreach (ProtocolReport report in reportsOfEquipment[equipment.EquipmentKey])
                equipmentOfReport.TryAdd(report.Identification, equipment.EquipmentKey);
        }

        DateOnly today = DateOnly.FromDateTime(_time.GetUtcNow().UtcDateTime);

        bool caughtUp = await ReadNewReportsAsync(scope, equipmentOfReport, today, budget, tally, cancellationToken);

        await RetryUnreadableAsync(scope.Cnpj, today, budget, tally, cancellationToken);

        await ProjectStatusesAsync(
            scope.Cnpj, equipmentList, reportsOfEquipment, caughtUp, tally, cancellationToken);

        await _store.SaveRunAsync(scope.Cnpj, _time.GetUtcNow().UtcDateTime, cancellationToken);
    }

    private async Task<bool> ReadNewReportsAsync(
        LoadScope scope,
        Dictionary<string, string> equipmentOfReport,
        DateOnly today,
        Budget budget,
        Tally tally,
        CancellationToken cancellationToken)
    {
        DateTime? from = scope.ProcessedUntilIssueDate?.AddDays(-ReportLoadOptions.SlackDays);
        DateTime processedUntil = scope.ProcessedUntilIssueDate ?? DateTime.MinValue;
        SignedReportCursor? cursor = null;
        int sourceFailures = 0;

        while (true)
        {
            IReadOnlyList<SignedReportPointer> batch = await _source.NextSignedReportsAsync(
                scope.Cnpj, from, cursor, _options.BatchSize, cancellationToken);

            if (batch.Count == 0) return true;

            Dictionary<Guid, ReadingControl> controls = (await _store.GetControlsAsync(
                    [.. batch.Select(pointer => pointer.SignedFileId).Distinct()], cancellationToken))
                .ToDictionary(control => control.SignedFileId);

            SignedReportPointer? last = null;
            bool stopped = false;

            foreach (SignedReportPointer pointer in batch)
            {
                cancellationToken.ThrowIfCancellationRequested();

                ReadingControl? control = controls.GetValueOrDefault(pointer.SignedFileId);

                if (ReadingPolicy.ShouldRead(control, today))
                {
                    if (sourceFailures >= _options.MaxConsecutiveSourceFailures)
                        tally.Problems.Add("O AutoLAB deixou de entregar os arquivos assinados durante a carga.");

                    if (budget.IsSpent || sourceFailures >= _options.MaxConsecutiveSourceFailures)
                    {
                        stopped = true;
                        break;
                    }

                    bool sourceFailed = await ReadOneAsync(
                        scope.Cnpj,
                        pointer,
                        equipmentOfReport.GetValueOrDefault(pointer.Identification),
                        control,
                        budget,
                        tally,
                        cancellationToken);

                    sourceFailures = sourceFailed ? sourceFailures + 1 : 0;
                }

                last = pointer;
            }

            if (last is not null)
            {
                if (last.IssueDate > processedUntil) processedUntil = last.IssueDate;

                await _store.SaveProgressAsync(scope.Cnpj, processedUntil, last.ReportId, cancellationToken);

                cursor = new SignedReportCursor(last.IssueDate, last.ReportId);
            }

            await ServeEquipmentAsync(tally, cancellationToken);

            if (stopped) return false;

            if (batch.Count < _options.BatchSize) return true;
        }
    }

    private async Task RetryUnreadableAsync(
        string cnpj,
        DateOnly today,
        Budget budget,
        Tally tally,
        CancellationToken cancellationToken)
    {
        IReadOnlyList<StoredReading> unreadable = await _store.GetUnreadableReadingsAsync(cnpj, cancellationToken);
        int sourceFailures = 0;

        foreach (StoredReading stored in unreadable.Where(stored => ReadingPolicy.ShouldRead(stored.Control, today)))
        {
            cancellationToken.ThrowIfCancellationRequested();

            if (budget.IsSpent || sourceFailures >= _options.MaxConsecutiveSourceFailures) return;

            bool sourceFailed = await ReadOneAsync(
                cnpj,
                new SignedReportPointer(
                    stored.ReportId,
                    stored.Identification,
                    stored.IssueDate ?? default,
                    stored.SignedFileId),
                stored.EquipmentKey,
                stored.Control,
                budget,
                tally,
                cancellationToken);

            sourceFailures = sourceFailed ? sourceFailures + 1 : 0;
        }
    }

    private async Task<bool> ReadOneAsync(
        string cnpj,
        SignedReportPointer pointer,
        string? equipmentKey,
        ReadingControl? control,
        Budget budget,
        Tally tally,
        CancellationToken cancellationToken)
    {
        SignedReportFindings findings;
        bool sourceFailed = false;

        try
        {
            findings = await _source.ReadAsync(pointer.SignedFileId, cancellationToken);
        }
        catch (ReportDocumentSourceUnavailableException)
        {
            findings = SignedReportFindings.Unreadable;
            sourceFailed = true;
        }

        await _store.SaveReadingAsync(
            new StoredReading(
                pointer.SignedFileId,
                pointer.ReportId,
                pointer.Identification,
                cnpj,
                equipmentKey,
                pointer.IssueDate == default ? null : pointer.IssueDate,
                findings.Outcome,
                SignedReportFindings.ExtractorVersion,
                _time.GetUtcNow().UtcDateTime,
                ReadingPolicy.NextAttempt(control),
                findings.Chromatography,
                findings.PhysicalChemical),
            cancellationToken);

        budget.Spend();
        tally.ReportsRead++;

        if (!findings.Readable) tally.ReportsUnreadable++;

        await Task.Delay(_options.PauseBetweenPdfs, _time, cancellationToken);

        return sourceFailed;
    }

    private async Task ProjectStatusesAsync(
        string cnpj,
        IReadOnlyList<TestedEquipment> equipmentList,
        Dictionary<string, List<ProtocolReport>> reportsOfEquipment,
        bool caughtUp,
        Tally tally,
        CancellationToken cancellationToken)
    {
        if (equipmentList.Count == 0) return;

        ILookup<string, ReadSummary> summaries = (await _store.GetReadSummariesAsync(cnpj, null, cancellationToken))
            .ToLookup(summary => summary.Identification, StringComparer.OrdinalIgnoreCase);

        string[] serialNumbers = [.. equipmentList
            .Select(equipment => equipment.SerialNumber)
            .OfType<string>()
            .Distinct(StringComparer.Ordinal)];

        Dictionary<string, EquipmentNameplate> nameplates = serialNumbers.Length == 0
            ? []
            : (await _nameplates.GetNameplatesOfAsync(serialNumbers, cancellationToken))
                .GroupBy(nameplate => nameplate.SerialNumber, StringComparer.Ordinal)
                .ToDictionary(found => found.Key, found => found.First(), StringComparer.Ordinal);

        DateTime now = _time.GetUtcNow().UtcDateTime;
        List<EquipmentStatus> statuses = [];

        foreach (TestedEquipment equipment in equipmentList)
        {
            ProjectedEquipment projected = ProjectedEquipment.From(
                equipment,
                equipment.SerialNumber is null ? null : nameplates.GetValueOrDefault(equipment.SerialNumber),
                equipment.Reports.Count,
                reportsOfEquipment[equipment.EquipmentKey]);

            List<ReadSummary> ofEquipment = [.. projected.Identifications
                .SelectMany(identification => summaries[identification])];

            if (caughtUp || ofEquipment.Count > 0)
                statuses.Add(EquipmentStatusRule.Of(cnpj, projected, ofEquipment, now));
        }

        await _store.SaveStatusesAsync(cnpj, statuses, cancellationToken);

        tally.EquipmentUpdated += statuses.Count;
    }

    private static SampleAnalysis AnalysisOf(CompanyProtocol protocol)
    {
        return new SampleAnalysis(
            protocol.ProtocoloLactec,
            protocol.Identification,
            protocol.SerialNumber,
            protocol.Company,
            protocol.IssueDate,
            protocol.AnalysisEndDate,
            []);
    }

    private static bool IsSourceFailure(Exception failure)
    {
        return failure is ReportDocumentSourceUnavailableException
            or LabTestSourceUnavailableException
            or NameplateSourceUnavailableException
            or ReportProjectionUnavailableException;
    }

    private sealed class Budget(int pdfs)
    {
        private int _left = pdfs;

        public bool IsSpent => _left <= 0;

        public void Spend() => _left--;
    }

    private sealed class Tally
    {
        public int ReportsRead { get; set; }

        public int ReportsUnreadable { get; set; }

        public int EquipmentUpdated { get; set; }

        public List<string> Problems { get; } = [];

        public ReportLoadResult ToResult()
        {
            return new ReportLoadResult(ReportsRead, ReportsUnreadable, EquipmentUpdated, [.. Problems]);
        }
    }
}
