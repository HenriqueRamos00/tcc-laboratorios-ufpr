using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata;

using Portal.Adapters.PortalStore;
using Portal.Application.LabResults;
using Portal.Application.Paging;
using Portal.Application.ReportDocuments;
using Portal.Application.ReportProjection;
using Portal.Tests.AutoLab;

using Xunit;

namespace Portal.Tests.ReportProjection;

public sealed class ReportProjectionStoreTests : IDisposable
{
    private static readonly DateTime Now = new(2026, 11, 10, 9, 0, 0, DateTimeKind.Utc);

    private readonly ProjectionTestStore _store = new();

    public void Dispose() => _store.Dispose();

    [Fact]
    public async Task TheListIsFilteredByStatusAndPagedMostRecentReportFirstInsideTheCompany()
    {
        await _store.Store().SaveStatusesAsync(
            TestCompanies.EnergyCnpj,
            [
                Status("A-1", OverallStatus.NonConforming, new DateTime(2026, 3, 1)),
                Status("A-2", OverallStatus.NonConforming, new DateTime(2026, 9, 1)),
                Status("A-3", OverallStatus.NonConforming, new DateTime(2026, 6, 1)),
                Status("B-1", OverallStatus.Warning, new DateTime(2026, 9, 2)),
                Status("C-1", OverallStatus.Normal, new DateTime(2026, 9, 3)),
                Status("D-1", OverallStatus.NoReport, null)
            ],
            CancellationToken.None);
        await _store.Store().SaveStatusesAsync(
            TestCompanies.DistributorCnpj,
            [Status("X-1", OverallStatus.NonConforming, new DateTime(2026, 10, 1), TestCompanies.DistributorCnpj)],
            CancellationToken.None);

        Page<EquipmentStatus> first = await ListAsync(OverallStatus.NonConforming, new PageRequest(1, 2));
        Page<EquipmentStatus> second = await ListAsync(OverallStatus.NonConforming, new PageRequest(2, 2));

        Assert.Equal(3, first.TotalCount);
        Assert.Equal(["A-2", "A-3"], first.Items.Select(Key));
        Assert.Equal(["A-1"], second.Items.Select(Key));
        Assert.Equal(["B-1"], (await ListAsync(OverallStatus.Warning)).Items.Select(Key));
        Assert.Equal(["C-1"], (await ListAsync(OverallStatus.Normal)).Items.Select(Key));
        Assert.Equal(["D-1"], (await ListAsync(OverallStatus.NoReport)).Items.Select(Key));
        Assert.Empty((await ListAsync(OverallStatus.Unclassified)).Items);
    }

    [Fact]
    public async Task TheSearchNarrowsTheFilteredListBySerialTagOrIdentification()
    {
        await _store.Store().SaveStatusesAsync(
            TestCompanies.EnergyCnpj,
            [
                Status("571289", OverallStatus.Normal, new DateTime(2026, 9, 1), tag: "TR-00011"),
                Status("999", OverallStatus.Normal, new DateTime(2026, 9, 2), tag: "RE-00002"),
                Status("123", OverallStatus.Normal, new DateTime(2026, 9, 3), identification: "LACTEC-07777/2026")
            ],
            CancellationToken.None);

        Assert.Equal(["571289"], (await ListAsync(OverallStatus.Normal, search: "5712")).Items.Select(Key));
        Assert.Equal(["999"], (await ListAsync(OverallStatus.Normal, search: "RE-0")).Items.Select(Key));
        Assert.Equal(["123"], (await ListAsync(OverallStatus.Normal, search: "07777")).Items.Select(Key));
        Assert.Empty((await ListAsync(OverallStatus.Normal, search: "nada")).Items);
    }

    [Fact]
    public async Task TheStatusOfAnEquipmentIsOnlySeenByItsCompany()
    {
        await _store.Store().SaveStatusesAsync(
            TestCompanies.EnergyCnpj,
            [Status("571289", OverallStatus.Warning, new DateTime(2026, 9, 1))],
            CancellationToken.None);

        Assert.Single(await _store.Store().GetStatusesAsync(
            [TestCompanies.EnergyCnpj], ["571289", "outro"], CancellationToken.None));
        Assert.Empty(await _store.Store().GetStatusesAsync(
            [TestCompanies.DistributorCnpj], ["571289"], CancellationToken.None));
    }

    [Fact]
    public async Task SavingAStatusAgainReplacesTheRowOfThatEquipment()
    {
        await _store.Store().SaveStatusesAsync(
            TestCompanies.EnergyCnpj, [Status("571289", OverallStatus.NoReport, null)], CancellationToken.None);
        await _store.Store().SaveStatusesAsync(
            TestCompanies.EnergyCnpj,
            [Status("571289", OverallStatus.Warning, new DateTime(2026, 9, 1))],
            CancellationToken.None);

        EquipmentStatus status = Assert.Single(await _store.Store().GetStatusesAsync(
            [TestCompanies.EnergyCnpj], ["571289"], CancellationToken.None));

        Assert.Equal(OverallStatus.Warning, status.Overall);
        Assert.Equal(new SectionStatus(Conformity.Warning, "Atenção"), status.Chromatography);
        Assert.Null(status.PhysicalChemical);
        Assert.Equal(new DateTime(2026, 9, 1), status.LastReportDate);
    }

    [Fact]
    public async Task AReadingIsKeptByItsSignedFileAndReplacedWhenReadAgain()
    {
        Guid file = Guid.NewGuid();
        StoredReading unreadable = new(
            file, Guid.NewGuid(), "LACTEC-00001/2026", TestCompanies.EnergyCnpj, "571289",
            new DateTime(2026, 9, 24), ReportReadOutcome.Unreadable, SignedReportFindings.ExtractorVersion, Now, 1, null, null);

        await _store.Store().SaveReadingAsync(unreadable, CancellationToken.None);
        await _store.Store().SaveReadingAsync(
            unreadable with
            {
                Outcome = ReportReadOutcome.Extracted,
                Attempts = 2,
                Chromatography = new ReportSection("Atenção", Conformity.Warning, "LAUDO", ["Primeiro.", "Segundo."])
                {
                    ResultColor = "#FFF176"
                }
            },
            CancellationToken.None);

        StoredReading stored = Assert.Single(await _store.Store().GetReadingsAsync(
            [TestCompanies.EnergyCnpj], ["LACTEC-00001/2026"], CancellationToken.None));

        Assert.Equal((ReportReadOutcome.Extracted, 2), (stored.Outcome, stored.Attempts));
        Assert.Equal(["Primeiro.", "Segundo."], stored.Chromatography?.Paragraphs);
        Assert.Equal("#FFF176", stored.Chromatography?.ResultColor);
        Assert.Empty(await _store.Store().GetReadingsAsync(
            [TestCompanies.DistributorCnpj], ["LACTEC-00001/2026"], CancellationToken.None));
        Assert.Equal(
            (file, ReportReadOutcome.Extracted, 2),
            (await _store.Store().GetControlsAsync([file, Guid.NewGuid()], CancellationToken.None))
                .Select(control => (control.SignedFileId, control.Outcome, control.Attempts)).Single());
    }

    [Fact]
    public async Task OnlyActiveCompaniesAreInTheScopeOfTheLoad()
    {
        _store.Activate(TestCompanies.EnergyCnpj);
        _store.Activate(TestCompanies.DistributorCnpj, active: false);

        LoadScope scope = Assert.Single(await _store.Store().GetActiveScopesAsync(CancellationToken.None));

        Assert.Equal(TestCompanies.EnergyCnpj, scope.Cnpj);
        Assert.Null(scope.ProcessedUntilIssueDate);
        Assert.Null(scope.LastRunAt);
    }

    [Fact]
    public void TheStatusTableHasTheIndexTheFilteredListUses()
    {
        using PortalStoreDbContext database = _store.Context();
        IEntityType table = database.Model.FindEntityType(typeof(EquipmentStatusRow))!;

        Assert.Equal("EquipmentStatuses", table.GetTableName());
        Assert.Contains(table.GetIndexes(), index => index.Properties.Select(property => property.Name).SequenceEqual(
            [nameof(EquipmentStatusRow.Cnpj), nameof(EquipmentStatusRow.OverallStatus), nameof(EquipmentStatusRow.LastReportDate)]));
        Assert.Equal(
            [nameof(ReportReadingRow.SignedFileId)],
            database.Model.FindEntityType(typeof(ReportReadingRow))!.FindPrimaryKey()!.Properties.Select(property => property.Name));
        Assert.Equal(
            [nameof(LoadScopeRow.Cnpj)],
            database.Model.FindEntityType(typeof(LoadScopeRow))!.FindPrimaryKey()!.Properties.Select(property => property.Name));
    }

    private Task<Page<EquipmentStatus>> ListAsync(OverallStatus status, PageRequest? page = null, string? search = null)
    {
        EquipmentSearch.TryCreate(search, out EquipmentSearch? term);

        return _store.Store().ListStatusesAsync(
            [TestCompanies.EnergyCnpj], status, term, page ?? PageRequest.First, CancellationToken.None);
    }

    private static string Key(EquipmentStatus status) => status.Equipment.EquipmentKey;

    private static EquipmentStatus Status(
        string key,
        OverallStatus overall,
        DateTime? lastReport,
        string cnpj = TestCompanies.EnergyCnpj,
        string? tag = null,
        string identification = "LACTEC-00001/2026")
    {
        Conformity? conformity = overall switch
        {
            OverallStatus.NonConforming => Conformity.NonConforming,
            OverallStatus.Warning => Conformity.Warning,
            OverallStatus.Normal => Conformity.Normal,
            _ => null
        };

        return new EquipmentStatus(
            cnpj,
            new ProjectedEquipment(
                key, key, key, tag, "TRANSFORMADOR", tag is not null, TestCompanies.Energy,
                identification, 1, new DateTime(2026, 8, 1), [identification]),
            lastReport,
            lastReport is null ? null : new SectionStatus(conformity, "Atenção"),
            null,
            overall,
            Now);
    }
}
