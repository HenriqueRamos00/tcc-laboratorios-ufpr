using System.Text.Json;

using Microsoft.Extensions.Caching.Memory;

using Portal.Adapters.AutoLab;
using Portal.Adapters.CompanyRecords;
using Portal.Adapters.LabResults;
using Portal.Adapters.PortalStore;
using Portal.Adapters.ReportDocuments;
using Portal.Application.LabResults;
using Portal.Application.ReportDocuments;
using Portal.Application.ReportProjection;
using Portal.Application.Visibility;
using Portal.Tests.AutoLab;
using Portal.Tests.Nameplate;
using Portal.Tests.ReportDocuments;

using Xunit;

namespace Portal.Tests.ReportProjection;

public sealed class ReportLoadTests : IDisposable
{
    private const string Red = "LACTEC-00001/2026";
    private const string Green = "LACTEC-00002/2026";
    private const string Unsigned = "LACTEC-00003/2026";
    private const string Foreign = "LACTEC-00009/2026";
    private const string Pcb = "LACTEC-00030/2026";
    private const string GasesAndPhysicalChemical = "LACTEC-00031/2026";
    private const string OnlyPcb = "LACTEC-00032/2026";

    private readonly AutoLabTestDatabase _autoLab = new();
    private readonly ProjectionTestStore _store = new();
    private readonly ReportLoadInProgress _inProgress = new();
    private readonly RecordedChannel _channel = new();
    private readonly MovableTime _time = new(new DateTimeOffset(2026, 11, 10, 12, 0, 0, TimeSpan.Zero));

    public ReportLoadTests()
    {
        _autoLab.AddTestRow("01/2026-001", "571289", TestCompanies.Energy, new DateTime(2026, 9, 20), Red);
        _autoLab.AddTestRow("02/2026-001", "TR-2", TestCompanies.Energy, new DateTime(2026, 9, 21), Green);
        _autoLab.AddTestRow("03/2026-001", "TR-3", TestCompanies.Energy, new DateTime(2026, 9, 22), Unsigned);
        _autoLab.AddTestRow("09/2026-001", "DX-1", TestCompanies.Distributor, new DateTime(2026, 9, 23), Foreign);

        _autoLab.AddReportIssue(
            Red,
            _autoLab.AddFile("vermelho.pdf", SyntheticReportPdf.Of("Descarga elétrica", null, gasFill: CellFill.Red)),
            published: new DateTime(2026, 9, 24));
        _autoLab.AddReportIssue(
            Green,
            _autoLab.AddFile("verde.pdf", SyntheticReportPdf.Of(
                "NORMAL", "NORMAL", gasFill: CellFill.Green, physicalChemicalFill: CellFill.Green)),
            published: new DateTime(2026, 9, 25));
        _autoLab.AddReportIssue(Unsigned, signedPdf: null, published: new DateTime(2026, 9, 26));
        _autoLab.AddReportIssue(
            Foreign,
            _autoLab.AddFile("alheio.pdf", SyntheticReportPdf.Of("Atenção", null, gasFill: CellFill.Yellow)),
            published: new DateTime(2026, 9, 27),
            company: TestCompanies.Distributor);

        _store.Activate(TestCompanies.EnergyCnpj);
    }

    public void Dispose()
    {
        _autoLab.Dispose();
        _store.Dispose();
    }

    [Fact]
    public async Task TheDailyRoundReadsOnlyTheActiveCompanyAndProjectsEveryEquipmentOfIt()
    {
        ReportLoadResult result = await Load().RunDailyAsync(CancellationToken.None);

        Assert.True(result.Success);
        Assert.Equal((2, 0, 3), (result.ReportsRead, result.ReportsUnreadable, result.EquipmentUpdated));
        Assert.Equal(2, FilesRead());

        List<ReportReadingRow> readings = _store.Readings();
        Assert.Equal([Red, Green], readings.Select(row => row.Identification));
        Assert.All(readings, row => Assert.Equal(TestCompanies.EnergyCnpj, row.Cnpj));
        Assert.All(readings, row => Assert.Equal("extraido", row.Outcome));
        Assert.All(readings, row => Assert.Equal(SignedReportFindings.ExtractorVersion, row.ExtractorVersion));
        Assert.Equal(["571289", "TR-2"], readings.Select(row => row.EquipmentKey));
        ReportSectionResultRow section = Assert.Single(readings[0].Sections);
        Assert.Equal(
            ("cromatografia", "Descarga elétrica", "nao-conforme"),
            (section.Section, section.ResultLabel, section.Conformity));
        Assert.Contains(SyntheticReportPdf.GasParagraph, section.Paragraphs, StringComparison.Ordinal);

        Assert.Equal(
            [("571289", "nao-conforme"), ("TR-2", "normal"), ("TR-3", "sem-laudo")],
            _store.Statuses().Select(row => (row.EquipmentKey, row.OverallStatus)));
        Assert.All(_store.Statuses(), row => Assert.Equal(TestCompanies.EnergyCnpj, row.Cnpj));

        LoadScopeRow scope = _store.ScopeOf(TestCompanies.EnergyCnpj);
        Assert.Equal(new DateTime(2026, 9, 25), scope.ProcessedUntilIssueDate);
        Assert.NotNull(scope.ProcessedUntilReportId);
        Assert.Equal(_time.GetUtcNow().UtcDateTime, scope.LastRunAt);
    }

    [Fact]
    public async Task AFileAlreadyReadIsNeverReadAgain()
    {
        await Load().RunDailyAsync(CancellationToken.None);

        ReportLoadResult again = await Load().RunDailyAsync(CancellationToken.None);

        Assert.Equal(0, again.ReportsRead);
        Assert.Equal(2, FilesRead());
        Assert.All(_store.Readings(), row => Assert.Equal(1, row.Attempts));
    }

    [Fact]
    public async Task TheNextRoundReadsOnlyWhatWasIssuedSinceTheLastOneWithOneDayOfSlack()
    {
        await Load().RunDailyAsync(CancellationToken.None);

        _autoLab.AddReportIssue(
            "LACTEC-00010/2026",
            _autoLab.AddFile("novo.pdf", SyntheticReportPdf.Of("NORMAL", null, gasFill: CellFill.Green)),
            published: new DateTime(2026, 9, 30));
        _autoLab.AddReportIssue(
            "LACTEC-00011/2026",
            _autoLab.AddFile("na-folga.pdf", SyntheticReportPdf.Of("NORMAL", null, gasFill: CellFill.Green)),
            published: new DateTime(2026, 9, 24, 6, 0, 0));
        _autoLab.AddReportIssue(
            "LACTEC-00012/2026",
            _autoLab.AddFile("antigo.pdf", SyntheticReportPdf.Of("NORMAL", null, gasFill: CellFill.Green)),
            published: new DateTime(2026, 9, 1));

        ReportLoadResult result = await Load().RunDailyAsync(CancellationToken.None);

        Assert.Equal(2, result.ReportsRead);
        Assert.Equal(
            [Red, Green, "LACTEC-00010/2026", "LACTEC-00011/2026"],
            _store.Readings().Select(row => row.Identification));
        Assert.Equal(new DateTime(2026, 9, 30), _store.ScopeOf(TestCompanies.EnergyCnpj).ProcessedUntilIssueDate);
    }

    [Fact]
    public async Task ACompanyActivatedLaterGetsItsWholeHistoryWhileTheOthersStayIncremental()
    {
        await Load().RunDailyAsync(CancellationToken.None);
        _store.Activate(TestCompanies.DistributorCnpj);

        ReportLoadResult result = await Load().RunDailyAsync(CancellationToken.None);

        Assert.Equal(1, result.ReportsRead);
        Assert.Equal(3, FilesRead());
        Assert.Equal(
            ("DX-1", "alerta", TestCompanies.DistributorCnpj),
            _store.Statuses().Where(row => row.EquipmentKey == "DX-1")
                .Select(row => (row.EquipmentKey, row.OverallStatus, row.Cnpj)).Single());
    }

    [Fact]
    public async Task TheFirstLoadSpreadsOverRoundsAndResumesWhereItStopped()
    {
        ReportLoadOptions oneAtATime = Options(maxPdfsPerRun: 1);

        ReportLoadResult first = await Load(oneAtATime).RunDailyAsync(CancellationToken.None);

        Assert.Equal(1, first.ReportsRead);
        Assert.Equal([("571289", "nao-conforme")], _store.Statuses().Select(row => (row.EquipmentKey, row.OverallStatus)));
        Assert.Equal(new DateTime(2026, 9, 24), _store.ScopeOf(TestCompanies.EnergyCnpj).ProcessedUntilIssueDate);

        ReportLoadResult second = await Load(oneAtATime).RunDailyAsync(CancellationToken.None);

        Assert.Equal(1, second.ReportsRead);
        Assert.Equal(2, FilesRead());
        Assert.Equal(
            [("571289", "nao-conforme"), ("TR-2", "normal"), ("TR-3", "sem-laudo")],
            _store.Statuses().Select(row => (row.EquipmentKey, row.OverallStatus)));
    }

    [Fact]
    public async Task TheReportsComeInBatchesAndEveryBatchIsFollowed()
    {
        ReportLoadResult result = await Load(Options(batchSize: 1)).RunDailyAsync(CancellationToken.None);

        Assert.Equal(2, result.ReportsRead);
        Assert.Equal(3, _autoLab.Commands.Count(command =>
            command.Contains("\"PdfAssinado\" IS NOT NULL", StringComparison.Ordinal)));
    }

    [Fact]
    public async Task APdfThatCannotBeReadDoesNotStopTheRoundAndIsTriedOnThreeDifferentDays()
    {
        _autoLab.AddReportIssue(
            "LACTEC-00020/2026",
            _autoLab.AddFile("corrompido.pdf", [.. "corrompido"u8]),
            published: new DateTime(2026, 9, 24, 12, 0, 0));

        ReportLoadResult first = await Load().RunDailyAsync(CancellationToken.None);

        Assert.True(first.Success);
        Assert.Equal((3, 1), (first.ReportsRead, first.ReportsUnreadable));
        Assert.Equal(("ilegivel", 1), Unreadable());
        Assert.Equal(2, _store.Readings().Count(row => row.Outcome == "extraido"));

        await Load().RunDailyAsync(CancellationToken.None);
        Assert.Equal(("ilegivel", 1), Unreadable());

        _time.Advance(TimeSpan.FromDays(1));
        await Load().RunDailyAsync(CancellationToken.None);
        Assert.Equal(("ilegivel", 2), Unreadable());

        _time.Advance(TimeSpan.FromDays(5));
        await Load().RunDailyAsync(CancellationToken.None);
        Assert.Equal(("ilegivel", 3), Unreadable());

        int filesRead = FilesRead();
        _time.Advance(TimeSpan.FromDays(1));
        ReportLoadResult afterTheLimit = await Load().RunDailyAsync(CancellationToken.None);

        Assert.Equal(0, afterTheLimit.ReportsRead);
        Assert.Equal(("ilegivel", 3), Unreadable());
        Assert.Equal(filesRead, FilesRead());
    }

    [Fact]
    public async Task WhoAsksDuringARoundBecomesInterestedAndNoOtherRoundIsOpened()
    {
        Assert.True(_inProgress.TryStart());

        ReportLoadResult daily = await Load().RunDailyAsync(CancellationToken.None);
        ReportLoadResult manual = await Load().RefreshEquipmentAsync(
            TestCompanies.ScopeOf(TestCompanies.Distributor), "DX-1", "42", CancellationToken.None);
        ReportLoadResult repeated = await Load().RefreshEquipmentAsync(
            TestCompanies.ScopeOf(TestCompanies.Distributor), "DX-1", "42", CancellationToken.None);

        Assert.True(daily.AlreadyRunning);
        Assert.True(manual.AlreadyRunning);
        Assert.True(repeated.AlreadyRunning);
        Assert.Equal(0, FilesRead());
        Assert.Empty(_channel.Published);

        _inProgress.Finish();
        ReportLoadResult round = await Load().RunDailyAsync(CancellationToken.None);

        Assert.False(round.AlreadyRunning);
        Assert.Equal(3, round.ReportsRead);
        Assert.Contains(_store.Statuses(), row => row.EquipmentKey == "DX-1" && row.OverallStatus == "alerta");

        (string name, string data, string[] users) = Assert.Single(_channel.Published);
        Assert.Equal("carga.concluida", name);
        Assert.Equal(["42"], users);
        using JsonDocument notice = JsonDocument.Parse(data);
        Assert.Equal("success", notice.RootElement.GetProperty("status").GetString());
        Assert.Equal(3, notice.RootElement.GetProperty("reportsRead").GetInt32());
        Assert.True(_inProgress.TryStart());
    }

    [Fact]
    public async Task TheManualRefreshReadsOneEquipmentInsideTheScopeOfWhoAsked()
    {
        ReportLoadResult outside = await Load().RefreshEquipmentAsync(
            TestCompanies.ScopeOf(TestCompanies.Energy), "DX-1", "42", CancellationToken.None);

        Assert.Equal((0, 0), (outside.ReportsRead, outside.EquipmentUpdated));
        Assert.Equal(0, FilesRead());
        Assert.Empty(_store.Statuses());
        Assert.Empty(_store.Readings());

        ReportLoadResult inside = await Load().RefreshEquipmentAsync(
            TestCompanies.ScopeOf(TestCompanies.Distributor), "DX-1", "77", CancellationToken.None);

        Assert.Equal((1, 1), (inside.ReportsRead, inside.EquipmentUpdated));
        Assert.Equal(1, FilesRead());
        EquipmentStatusRow status = Assert.Single(_store.Statuses());
        Assert.Equal(
            ("DX-1", TestCompanies.DistributorCnpj, "alerta", "Atenção"),
            (status.EquipmentKey, status.Cnpj, status.OverallStatus, status.ChromatographyLabel));
        Assert.Equal(["42", "77"], _channel.Published.SelectMany(published => published.UserKeys));
    }

    [Fact]
    public async Task AnEquipmentWithoutSignedReportIsProjectedAsWithoutReportByTheManualRefresh()
    {
        ReportLoadResult result = await Load().RefreshEquipmentAsync(
            TestCompanies.ScopeOf(TestCompanies.Energy), "TR-3", "42", CancellationToken.None);

        Assert.Equal((0, 1), (result.ReportsRead, result.EquipmentUpdated));
        Assert.Equal(("TR-3", "sem-laudo"), _store.Statuses().Select(row => (row.EquipmentKey, row.OverallStatus)).Single());
    }

    [Fact]
    public async Task TheBatchOfVisibleEquipmentIsServedInsideTheScopeOfWhoAsked()
    {
        ReportLoadAcceptance accepted = await Load().RequestEquipmentAsync(
            TestCompanies.ScopeOf(TestCompanies.Energy), ["571289", " DX-1 ", "571289", " "], "42", CancellationToken.None);

        Assert.Equal(new ReportLoadAcceptance(2, AlreadyRunning: false), accepted);
        Assert.Equal(0, FilesRead());
        Assert.Empty(_channel.Published);

        ReportLoadResult served = await Load().ServeRequestsAsync(CancellationToken.None);

        Assert.True(served.Success);
        Assert.Equal((1, 1), (served.ReportsRead, served.EquipmentUpdated));
        Assert.Equal(1, FilesRead());
        Assert.Equal(
            [("571289", TestCompanies.EnergyCnpj, "nao-conforme")],
            _store.Statuses().Select(row => (row.EquipmentKey, row.Cnpj, row.OverallStatus)));
        Assert.Equal(["42"], Assert.Single(_channel.Published).UserKeys);
    }

    [Fact]
    public async Task ARepeatedBatchDoesNotRepeatTheWork()
    {
        CompanyScope scope = TestCompanies.ScopeOf(TestCompanies.Energy);

        await Load().RequestEquipmentAsync(scope, ["571289", "TR-2"], "42", CancellationToken.None);
        ReportLoadAcceptance repeated = await Load().RequestEquipmentAsync(
            scope, ["571289", "TR-2"], "77", CancellationToken.None);

        Assert.Equal(2, repeated.Accepted);

        ReportLoadResult served = await Load().ServeRequestsAsync(CancellationToken.None);

        Assert.Equal((2, 2), (served.ReportsRead, served.EquipmentUpdated));
        Assert.Equal(["42", "77"], Assert.Single(_channel.Published).UserKeys);

        int commands = _autoLab.Commands.Count;
        ReportLoadAcceptance afterwards = await Load().RequestEquipmentAsync(
            scope, ["571289", "TR-2"], "42", CancellationToken.None);

        Assert.Equal(ReportLoadAcceptance.Nothing, afterwards);
        Assert.Empty(_inProgress.TakeRequests());
        Assert.Empty(_inProgress.HarvestInterested());

        _inProgress.Enqueue("42", [new EquipmentRefreshRequest(scope, "571289", OnlyIfMissing: true)], []);
        ReportLoadResult again = await Load().ServeRequestsAsync(CancellationToken.None);

        Assert.Equal((0, 0), (again.ReportsRead, again.EquipmentUpdated));
        Assert.Equal(commands, _autoLab.Commands.Count);
        Assert.Equal(2, FilesRead());
    }

    [Fact]
    public async Task TheRestOfTheClientAskedDuringARoundBecomesInterestedAndIsServedByTheSameSlot()
    {
        CompanyScope scope = TestCompanies.ScopeOf(TestCompanies.Distributor);

        Assert.True(_inProgress.TryStart());

        ReportLoadAcceptance accepted = Load().RequestCompanies(scope, "42");
        ReportLoadAcceptance repeated = Load().RequestCompanies(scope, "42");
        ReportLoadResult refused = await Load().ServeRequestsAsync(CancellationToken.None);

        Assert.Equal(new ReportLoadAcceptance(1, AlreadyRunning: true), accepted);
        Assert.Equal(new ReportLoadAcceptance(1, AlreadyRunning: true), repeated);
        Assert.True(refused.AlreadyRunning);
        Assert.Equal(0, FilesRead());
        Assert.Empty(_channel.Published);

        _inProgress.Finish();
        ReportLoadResult served = await Load().ServeRequestsAsync(CancellationToken.None);

        Assert.True(served.Success);
        Assert.Equal((1, 1), (served.ReportsRead, served.EquipmentUpdated));
        Assert.Equal(1, FilesRead());
        Assert.Equal(
            [("DX-1", TestCompanies.DistributorCnpj, "alerta")],
            _store.Statuses().Select(row => (row.EquipmentKey, row.Cnpj, row.OverallStatus)));
        Assert.Equal(["42"], Assert.Single(_channel.Published).UserKeys);
        Assert.False(_inProgress.IsRunning);
    }

    [Fact]
    public async Task TheRestOfTheClientStopsAtTheCeilingOfCompaniesAndOfPdfs()
    {
        CompanyScope both = TestCompanies.ScopeOf(TestCompanies.Energy, TestCompanies.Distributor);
        ReportLoadOptions options = Options();
        options.MaxCompaniesPerRefresh = 1;
        options.MaxPdfsPerRefresh = 1;

        ReportLoadAcceptance accepted = Load(options).RequestCompanies(both, "42");

        Assert.Equal(1, accepted.Accepted);
        Assert.Equal([both.Cnpjs[0]], _inProgress.TakeCompanies());

        Load(options).RequestCompanies(TestCompanies.ScopeOf(TestCompanies.Energy), "42");
        ReportLoadResult served = await Load(options).ServeRequestsAsync(CancellationToken.None);

        Assert.Equal(1, served.ReportsRead);
        Assert.Equal(1, FilesRead());
        Assert.All(_store.Statuses(), row => Assert.Equal(TestCompanies.EnergyCnpj, row.Cnpj));
    }

    [Fact]
    public async Task ACancelledRoundFreesTheSlotAndTellsWhoWasWaitingThatItFailed()
    {
        _inProgress.RegisterInterest("42");

        await Assert.ThrowsAnyAsync<OperationCanceledException>(() =>
            Load().RunDailyAsync(new CancellationToken(canceled: true)));

        Assert.True(_inProgress.TryStart());
        using JsonDocument notice = JsonDocument.Parse(Assert.Single(_channel.Published).DataJson);
        Assert.Equal("failure", notice.RootElement.GetProperty("status").GetString());
    }

    [Fact]
    public async Task TheStartupRoundIsDueWhenACompanyNeverRanOrTodayWasMissed()
    {
        DateTime today = new(2026, 11, 10, 3, 0, 0, DateTimeKind.Utc);
        ReportLoad load = Load();

        Assert.True(await load.IsDueAtStartupAsync(today, pastTheDailyTime: false, CancellationToken.None));

        await _store.Store().SaveRunAsync(TestCompanies.EnergyCnpj, today.AddHours(-10), CancellationToken.None);

        Assert.False(await load.IsDueAtStartupAsync(today, pastTheDailyTime: false, CancellationToken.None));
        Assert.True(await load.IsDueAtStartupAsync(today, pastTheDailyTime: true, CancellationToken.None));

        await _store.Store().SaveRunAsync(TestCompanies.EnergyCnpj, today.AddHours(4), CancellationToken.None);

        Assert.False(await load.IsDueAtStartupAsync(today, pastTheDailyTime: true, CancellationToken.None));
    }

    [Fact]
    public async Task WithoutAnyActiveCompanyNothingIsDueAndNothingIsRead()
    {
        using ProjectionTestStore empty = new();
        ReportLoad load = Load(store: empty);

        Assert.False(await load.IsDueAtStartupAsync(DateTime.UtcNow, pastTheDailyTime: true, CancellationToken.None));

        ReportLoadResult result = await load.RunDailyAsync(CancellationToken.None);

        Assert.Equal(0, result.ReportsRead);
        Assert.Empty(_autoLab.Commands);
    }

    [Fact]
    public async Task TheIndicatorsScreenReadsThePdfOnceAndThenTheLocalProjection()
    {
        SampleHealthIndicators? first = await Consultation().GetHealthIndicatorsAsync(
            new HealthIndicatorsQuery(TestCompanies.ScopeOf(TestCompanies.Energy), "571289", null),
            CancellationToken.None);

        Assert.Equal(Conformity.NonConforming, first?.GasConformity);
        Assert.Equal(1, FilesRead());
        Assert.Equal(("571289", "nao-conforme"), _store.Statuses().Select(row => (row.EquipmentKey, row.OverallStatus)).Single());

        SampleHealthIndicators? second = await Consultation().GetHealthIndicatorsAsync(
            new HealthIndicatorsQuery(TestCompanies.ScopeOf(TestCompanies.Energy), "571289", null),
            CancellationToken.None);

        Assert.Equal(1, FilesRead());
        Assert.Equal(Conformity.NonConforming, second?.GasConformity);
        Assert.Equal("Descarga elétrica", second?.GasResult);
        Assert.Equal([SyntheticReportPdf.GasParagraph, SyntheticReportPdf.NewAnalysis], second?.GasReports[0].Paragraphs);
    }

    [Fact]
    public async Task TheDailyRoundReadsEveryReportOfAProtocolAndTheOneWithSectionsDecidesTheStatus()
    {
        AddSwitchWithTwoReportsOnOneProtocol();

        ReportLoadResult result = await Load().RunDailyAsync(CancellationToken.None);

        Assert.True(result.Success);
        Assert.Equal(
            [(Pcb, "sem-secoes", "CM-1"), (GasesAndPhysicalChemical, "extraido", "CM-1")],
            _store.Readings()
                .Where(row => row.EquipmentKey == "CM-1")
                .Select(row => (row.Identification, row.Outcome, row.EquipmentKey!)));

        EquipmentStatusRow status = _store.Statuses().Single(row => row.EquipmentKey == "CM-1");
        Assert.Equal(
            ("alerta", "NORMAL", "normal", "Atenção", "alerta"),
            (status.OverallStatus, status.ChromatographyLabel, status.ChromatographyConformity,
                status.PhysicalChemicalLabel, status.PhysicalChemicalConformity));
        Assert.Equal(new DateTime(2026, 11, 20), status.LastReportDate);
        Assert.Equal((GasesAndPhysicalChemical, 1), (status.Identification, status.ReportCount));
    }

    [Fact]
    public async Task TheManualRefreshReadsEveryReportOfTheProtocolsOfTheEquipment()
    {
        AddSwitchWithTwoReportsOnOneProtocol();

        ReportLoadResult result = await Load().RefreshEquipmentAsync(
            TestCompanies.ScopeOf(TestCompanies.Energy), "CM-1", "42", CancellationToken.None);

        Assert.Equal((2, 0, 1), (result.ReportsRead, result.ReportsUnreadable, result.EquipmentUpdated));
        Assert.Equal(
            [(Pcb, "sem-secoes"), (GasesAndPhysicalChemical, "extraido")],
            _store.Readings().Select(row => (row.Identification, row.Outcome)));
        Assert.Equal(
            ("CM-1", "alerta", GasesAndPhysicalChemical),
            _store.Statuses().Select(row => (row.EquipmentKey, row.OverallStatus, row.Identification)).Single());

        int filesRead = FilesRead();
        ReportLoadResult again = await Load().RefreshEquipmentAsync(
            TestCompanies.ScopeOf(TestCompanies.Energy), "CM-1", "42", CancellationToken.None);

        Assert.Equal(0, again.ReportsRead);
        Assert.Equal(filesRead, FilesRead());
    }

    [Fact]
    public async Task AnEquipmentWhoseOnlyReportHasNoSectionsIsWithoutReportAndTheReadingIsKept()
    {
        _autoLab.AddTestRow(
            "06/2026-001", "PC-1", TestCompanies.Energy, new DateTime(2026, 11, 12), OnlyPcb,
            name: "Determinação de PCB", result: "2", issued: new DateTime(2026, 11, 12));
        _autoLab.AddReportIssue(
            OnlyPcb,
            _autoLab.AddFile("so-pcb.pdf", SyntheticReportPdf.Of(null, null)),
            published: new DateTime(2026, 11, 12));

        ReportLoadResult manual = await Load().RefreshEquipmentAsync(
            TestCompanies.ScopeOf(TestCompanies.Energy), "PC-1", "42", CancellationToken.None);

        Assert.Equal((1, 0, 1), (manual.ReportsRead, manual.ReportsUnreadable, manual.EquipmentUpdated));
        Assert.Equal((OnlyPcb, "sem-secoes"), _store.Readings().Select(row => (row.Identification, row.Outcome)).Single());
        EquipmentStatusRow afterTheRefresh = _store.Statuses().Single();
        Assert.Equal(("PC-1", "sem-laudo"), (afterTheRefresh.EquipmentKey, afterTheRefresh.OverallStatus));
        Assert.Null(afterTheRefresh.LastReportDate);

        int filesRead = FilesRead();
        await Load().RunDailyAsync(CancellationToken.None);

        Assert.Equal(filesRead + 2, FilesRead());
        Assert.Equal("sem-laudo", _store.Statuses().Single(row => row.EquipmentKey == "PC-1").OverallStatus);
    }

    [Fact]
    public async Task TheIndicatorsShowTheReportWithSectionsOfAProtocolThatAlsoHasOneWithout()
    {
        AddSwitchWithTwoReportsOnOneProtocol();

        SampleHealthIndicators? indicators = await Consultation().GetHealthIndicatorsAsync(
            new HealthIndicatorsQuery(TestCompanies.ScopeOf(TestCompanies.Energy), "CM-1", "05/2026-001"),
            CancellationToken.None);

        Assert.NotNull(indicators);
        Assert.Equal(GasesAndPhysicalChemical, indicators.Identification);
        Assert.Equal(GasesAndPhysicalChemical, Assert.Single(indicators.GasReports).Identification);
        Assert.Equal(GasesAndPhysicalChemical, Assert.Single(indicators.PhysicalChemicalReports).Identification);
        Assert.Equal(("NORMAL", Conformity.Normal), (indicators.GasResult, indicators.GasConformity));
        Assert.Equal(
            ("Atenção", Conformity.Warning),
            (indicators.PhysicalChemicalResult, indicators.PhysicalChemicalConformity));
        Assert.Equal(new DateTime(2026, 11, 20), indicators.GasReports[0].Date);
        Assert.Equal(2, FilesRead());
        Assert.Equal(
            [(Pcb, "sem-secoes"), (GasesAndPhysicalChemical, "extraido")],
            _store.Readings().Select(row => (row.Identification, row.Outcome)));
        Assert.Equal(("CM-1", "alerta"), _store.Statuses().Select(row => (row.EquipmentKey, row.OverallStatus)).Single());
    }

    private void AddSwitchWithTwoReportsOnOneProtocol()
    {
        _autoLab.AddTestRow(
            "05/2026-001", "CM-1", TestCompanies.Energy, new DateTime(2026, 11, 12), Pcb,
            name: "Determinação de PCB", result: "2", issued: new DateTime(2026, 11, 12));
        _autoLab.AddTestRow(
            "05/2026-001", "CM-1", TestCompanies.Energy, new DateTime(2026, 11, 9), GasesAndPhysicalChemical,
            issued: new DateTime(2026, 11, 20));
        _autoLab.AddTestRow(
            "05/2026-001", "CM-1", TestCompanies.Energy, new DateTime(2026, 11, 9), GasesAndPhysicalChemical,
            name: "Rigidez Dielétrica - Calota (kV)", result: "48", issued: new DateTime(2026, 11, 20));

        _autoLab.AddReportIssue(
            Pcb,
            _autoLab.AddFile("pcb.pdf", SyntheticReportPdf.Of(null, null)),
            published: new DateTime(2026, 11, 12));
        _autoLab.AddReportIssue(
            GasesAndPhysicalChemical,
            _autoLab.AddFile("gases-e-fisico-quimica.pdf", SyntheticReportPdf.Of(
                "NORMAL", "Atenção", gasFill: CellFill.Green, physicalChemicalFill: CellFill.Yellow)),
            published: new DateTime(2026, 11, 20));
    }

    private (string Outcome, int Attempts) Unreadable()
    {
        ReportReadingRow row = _store.Readings().Single(reading => reading.Identification == "LACTEC-00020/2026");

        return (row.Outcome, row.Attempts);
    }

    private int FilesRead()
    {
        return _autoLab.Commands.Count(command => command.Contains("\"Content\"", StringComparison.Ordinal));
    }

    private static ReportLoadOptions Options(int maxPdfsPerRun = 2000, int batchSize = 100)
    {
        return new ReportLoadOptions
        {
            PauseBetweenPdfsMilliseconds = 0,
            MaxPdfsPerRun = maxPdfsPerRun,
            BatchSize = batchSize
        };
    }

    private ReportLoad Load(ReportLoadOptions? options = null, ProjectionTestStore? store = null)
    {
        AutoLabDbContext context = _autoLab.NewContext();
        AutoLabQueryLimits limits = new();
        AutoLabCompanyRecordsSource records = _autoLab.CompanyRecords(context, limits);
        SqlServerReportProjectionStore projection = (store ?? _store).Store();

        return new ReportLoad(
            _inProgress,
            projection,
            new AutoLabSignedReportSource(context, records, limits),
            new AutoLabLabTestRepository(context, records, limits),
            new InMemoryEquipmentNameplateRepository(),
            new EquipmentProjection(projection, Issues(context, records, limits), _time),
            _channel,
            options ?? Options(),
            _time);
    }

    private LabResultsConsultation Consultation()
    {
        AutoLabDbContext context = _autoLab.NewContext();
        AutoLabQueryLimits limits = new();
        AutoLabCompanyRecordsSource records = _autoLab.CompanyRecords(context, limits);
        AutoLabReportIssueRepository issues = Issues(context, records, limits);

        return new LabResultsConsultation(
            new AutoLabLabTestRepository(context, records, limits),
            new InMemoryEquipmentNameplateRepository(),
            issues,
            new EquipmentProjection(_store.Store(), issues, _time));
    }

    private static AutoLabReportIssueRepository Issues(
        AutoLabDbContext context,
        AutoLabCompanyRecordsSource records,
        AutoLabQueryLimits limits)
    {
        return new AutoLabReportIssueRepository(context, records, limits, new MemoryCache(new MemoryCacheOptions()));
    }
}
