using Portal.Adapters.AutoLab;
using Portal.Adapters.LabResults;
using Portal.Application.LabResults;
using Portal.Application.LabResults.Exceptions;
using Portal.Application.Paging;
using Portal.Domain.LabResults;

using Portal.Application.Visibility;

using Xunit;

namespace Portal.Tests.AutoLab;

public sealed class AutoLabLabTestRepositoryTests : IDisposable
{
    private const string Tenant = "EMPRESA DE ENERGIA";
    private const string OtherTenant = "OUTRA EMPRESA S.A.";

    private readonly AutoLabTestDatabase _database = new();

    public void Dispose()
    {
        _database.Dispose();
    }

    private Task<Page<SampleAnalysis>> SearchAsync(string text, PageRequest page)
    {
        Assert.True(EquipmentSearch.TryCreate(text, out EquipmentSearch? search));
        Assert.NotNull(search);

        return Repository().GetAnalysesAsync(
            TestCompanies.ScopeOf(Tenant), search, page, CancellationToken.None);
    }

    private async Task<string[]> KeysFoundAsync(string text)
    {
        Page<SampleAnalysis> page = await SearchAsync(text, PageRequest.First);

        Assert.Equal(page.TotalCount, page.Items.Select(item => item.EquipmentKey).Distinct().Count());

        return [.. page.Items.Select(item => item.EquipmentKey).Distinct().Order(StringComparer.Ordinal)];
    }

    [Fact]
    public async Task TheTenantListingOnlyBringsTheTenantsEquipmentAndIgnoresCase()
    {
        _database.AddTestRow("P-1", "571289", Tenant, new DateTime(2026, 6, 1));
        _database.AddTestRow("P-2", "111.594/1", "empresa de energia", new DateTime(2026, 5, 1));
        _database.AddTestRow("P-3", "5233", OtherTenant, new DateTime(2026, 7, 1));

        Page<SampleAnalysis> page = await Repository()
            .GetAnalysesAsync(TestCompanies.ScopeOf(Tenant), null, PageRequest.First, CancellationToken.None);

        Assert.Equal(["111.594/1", "571289"], page.Items.Select(item => item.EquipmentKey).Order());
        Assert.False(page.HasNext);
        Assert.Equal(2, page.TotalCount);
        Assert.DoesNotContain(page.Items, item => item.EquipmentKey == "5233");
    }

    [Fact]
    public async Task TheSearchFindsBySerialNumberReportIdentificationAndNameplateTag()
    {
        _database.AddTestRow("P-1", "571289", Tenant, new DateTime(2026, 6, 1), "LACTEC-02272/2026");
        _database.AddTestRow("P-2", "884120", Tenant, new DateTime(2026, 5, 1), "LACTEC-02465/2026");
        _database.AddTestRow("P-3", " 111.594/1 ", Tenant, new DateTime(2026, 4, 1), "LACTEC-02665/2026");
        _database.AddTestRow("P-4", null, Tenant, new DateTime(2026, 3, 1), "LACTEC-02820/2026");
        _database.AddEquipment("111.594/1 ", "TR-00002", "230");

        Assert.Equal(["571289"], await KeysFoundAsync("7128"));
        Assert.Equal(["884120"], await KeysFoundAsync("lactec-02465"));
        Assert.Equal(["111.594/1"], await KeysFoundAsync("tr-0000"));
        Assert.Equal(["P-4"], await KeysFoundAsync("02820/2026"));
        Assert.Equal(["111.594/1", "571289", "884120", "P-4"], await KeysFoundAsync("2"));
        Assert.Empty(await KeysFoundAsync("nao-existe"));
    }

    [Fact]
    public async Task TheSearchNeverLeavesTheTenantNotEvenThroughTheRegistry()
    {
        _database.AddTestRow("P-1", "571289", Tenant, new DateTime(2026, 6, 1));
        _database.AddTestRow("P-2", "5233", OtherTenant, new DateTime(2026, 7, 1));
        _database.AddTestRow("P-3", null, Tenant, new DateTime(2026, 5, 1), "LACTEC-00009/2026");
        _database.AddTestRow("P-4", "  ", Tenant, new DateTime(2026, 4, 1), "LACTEC-00008/2026");
        _database.AddEquipment("5233", "TR-00004", "230");
        _database.AddEquipment(null, "TR-00004", "230");
        _database.AddEquipment("", "TR-00004", "230");

        Page<SampleAnalysis> byTag = await SearchAsync("TR-00004", PageRequest.First);
        Page<SampleAnalysis> bySerial = await SearchAsync("5233", PageRequest.First);

        Assert.Empty(byTag.Items);
        Assert.Equal(0, byTag.TotalCount);
        Assert.Empty(bySerial.Items);
        Assert.Equal(0, bySerial.TotalCount);
    }

    [Theory]
    [InlineData("%")]
    [InlineData("_")]
    [InlineData("[0-9]")]
    [InlineData("5%9")]
    [InlineData("57128_")]
    [InlineData("5712[89]")]
    public async Task TheWildcardsOfTheUserAreLiteralText(string text)
    {
        _database.AddTestRow("P-1", "571289", Tenant, new DateTime(2026, 6, 1));
        _database.AddTestRow("P-2", "884120", Tenant, new DateTime(2026, 5, 1));

        Assert.Empty(await KeysFoundAsync(text));
    }

    [Fact]
    public async Task ASerialNumberThatCarriesAWildcardIsFoundByIt()
    {
        _database.AddTestRow("P-1", "50%_[A]\\1", Tenant, new DateTime(2026, 6, 1));
        _database.AddTestRow("P-2", "50XY[A]\\1", Tenant, new DateTime(2026, 5, 1));

        Assert.Equal(["50%_[A]\\1"], await KeysFoundAsync("0%_["));
        Assert.Equal(["50%_[A]\\1", "50XY[A]\\1"], await KeysFoundAsync("[a]\\1"));
    }

    [Fact]
    public async Task TheSearchIsPagedByItsOwnTotalAndBringsTheWholeEquipment()
    {
        _database.AddTestRow("P-1", "A-100", Tenant, new DateTime(2026, 6, 1), "LACTEC-00001/2026");
        _database.AddTestRow("P-2", "A-100", Tenant, new DateTime(2026, 2, 1), "LACTEC-00002/2026");
        _database.AddTestRow("P-3", "A-200", Tenant, new DateTime(2026, 5, 1));
        _database.AddTestRow("P-4", "A-300", Tenant, new DateTime(2026, 4, 1));
        _database.AddTestRow("P-5", "B-100", Tenant, new DateTime(2026, 7, 1));

        Page<SampleAnalysis> first = await SearchAsync("a-", new PageRequest(1, 2));
        Page<SampleAnalysis> second = await SearchAsync("a-", new PageRequest(2, 2));
        Page<SampleAnalysis> byOldReport = await SearchAsync("00002/2026", PageRequest.First);

        Assert.Equal(3, first.TotalCount);
        Assert.True(first.HasNext);
        Assert.Equal(["A-100", "A-200"], first.Items.Select(item => item.EquipmentKey).Distinct().Order());
        Assert.Equal(3, second.TotalCount);
        Assert.False(second.HasNext);
        Assert.Equal(["A-300"], second.Items.Select(item => item.EquipmentKey).Distinct());
        Assert.Equal(1, byOldReport.TotalCount);
        Assert.Equal(["P-1", "P-2"], byOldReport.Items.Select(item => item.ProtocoloLactec).Order());
    }

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("  ")]
    public async Task ARegistryRowWithoutSerialDoesNotMatchEveryReportWithoutSerial(string? blankSerial)
    {
        _database.AddTestRow("P-9", blankSerial, Tenant, new DateTime(2026, 4, 1), "LACTEC-00009/2026");
        _database.AddEquipment(blankSerial, "TR-SEM-SERIE", "230");

        Page<SampleAnalysis> page = await SearchAsync("SEM-SERIE", PageRequest.First);

        Assert.Empty(page.Items);
        Assert.Equal(0, page.TotalCount);
    }

    [Fact]
    public async Task TheSearchKeepsTheListingOrderOfTheLatestReportOfTheWholeEquipment()
    {
        _database.AddTestRow("P-1", "A-100", Tenant, new DateTime(2026, 6, 1), "LACTEC-00001/2026");
        _database.AddTestRow("P-2", "A-100", Tenant, new DateTime(2020, 1, 1), "LACTEC-09999/2020");
        _database.AddTestRow("P-3", "B-100", Tenant, new DateTime(2024, 1, 1), "LACTEC-19999/2024");

        Page<SampleAnalysis> first = await SearchAsync("9999/", new PageRequest(1, 1));

        Assert.Equal(2, first.TotalCount);
        Assert.Equal(["A-100"], first.Items.Select(item => item.EquipmentKey).Distinct());
    }

    [Fact]
    public async Task TheTenantListingFindsRowsWhoseCompanyCarriesSurroundingBlanks()
    {
        _database.AddTestRow("P-1", "571289", " " + Tenant, new DateTime(2026, 6, 1));
        _database.AddTestRow("P-2", "111.594/1", Tenant + " ", new DateTime(2026, 5, 1));
        _database.AddTestRow("P-3", "5233", " " + OtherTenant + " ", new DateTime(2026, 7, 1));

        Page<SampleAnalysis> page = await Repository()
            .GetAnalysesAsync(TestCompanies.ScopeOf(Tenant), null, PageRequest.First, CancellationToken.None);

        Assert.Equal(["111.594/1", "571289"], page.Items.Select(item => item.EquipmentKey).Order());
        Assert.DoesNotContain(page.Items, item => item.EquipmentKey == "5233");
    }

    [Fact]
    public async Task TwoSerialNumbersThatDifferOnlyByCaseAreTwoEquipments()
    {
        _database.AddTestRow("P-1", "ENSAIO-TESTE", Tenant, new DateTime(2026, 6, 1));
        _database.AddTestRow("P-2", "ensaio-teste", Tenant, new DateTime(2026, 5, 1));

        Page<SampleAnalysis> page = await Repository()
            .GetAnalysesAsync(TestCompanies.ScopeOf(Tenant), null, PageRequest.First, CancellationToken.None);

        Assert.Equal(2, page.TotalCount);
        Assert.Equal(
            ["ENSAIO-TESTE", "ensaio-teste"],
            page.Items.Select(item => item.EquipmentKey).Order(StringComparer.Ordinal));
        Assert.Equal(
            ["P-1", "P-2"],
            page.Items.Select(item => item.ProtocoloLactec).Order(StringComparer.Ordinal));
    }

    [Fact]
    public async Task TheHistoryOfOneCaseDoesNotCountTheOtherCase()
    {
        _database.AddTestRow("P-1", "ENSAIO-TESTE", Tenant, new DateTime(2026, 6, 1));
        _database.AddTestRow("P-2", "ensaio-teste", Tenant, new DateTime(2026, 5, 1));

        Page<SampleAnalysis> history = await Repository().GetAnalysesOfEquipmentAsync(
            TestCompanies.ScopeOf(Tenant), "ensaio-teste", PageRequest.First, CancellationToken.None);

        Assert.Equal(1, history.TotalCount);
        Assert.Equal(["P-2"], history.Items.Select(item => item.ProtocoloLactec));
    }

    [Fact]
    public async Task TheListingPagesTheEquipmentKeysInTheDatabaseFromTheMostRecent()
    {
        _database.AddTestRow("P-1", "A", Tenant, new DateTime(2026, 1, 1));
        _database.AddTestRow("P-2", "B", Tenant, new DateTime(2026, 2, 1));
        _database.AddTestRow("P-3", "C", Tenant, new DateTime(2026, 3, 1));
        _database.AddTestRow("P-4", "C", Tenant, new DateTime(2025, 3, 1));
        _database.AddTestRow("P-5", null, Tenant, new DateTime(2026, 4, 1));

        AutoLabLabTestRepository repository = Repository();

        Page<SampleAnalysis> first = await repository
            .GetAnalysesAsync(TestCompanies.ScopeOf(Tenant), null, new PageRequest(1, 2), CancellationToken.None);
        Page<SampleAnalysis> second = await repository
            .GetAnalysesAsync(TestCompanies.ScopeOf(Tenant), null, new PageRequest(2, 2), CancellationToken.None);
        Page<SampleAnalysis> third = await repository
            .GetAnalysesAsync(TestCompanies.ScopeOf(Tenant), null, new PageRequest(3, 2), CancellationToken.None);

        Assert.Equal(["C", "P-5"], first.Items.Select(item => item.EquipmentKey).Distinct().Order());
        Assert.Equal(2, first.Items.Count(item => item.EquipmentKey == "C"));
        Assert.True(first.HasNext);
        Assert.Equal(4, first.TotalCount);
        Assert.Equal(["A", "B"], second.Items.Select(item => item.EquipmentKey).Order());
        Assert.False(second.HasNext);
        Assert.Empty(third.Items);
        Assert.Equal(4, third.TotalCount);
    }

    [Fact]
    public async Task TheListingCountsPagesTheKeysAndReadsOnlyTheRowsOfThePage()
    {
        _database.AddTestRow("P-1", "A", Tenant, new DateTime(2026, 1, 1));
        _database.AddTestRow("P-2", "B", Tenant, new DateTime(2026, 2, 1));

        await Repository().GetAnalysesAsync(TestCompanies.ScopeOf(Tenant), null, new PageRequest(1, 10), CancellationToken.None);

        Assert.Equal(5, _database.Commands.Count);
        Assert.All(_database.Commands, sql => Assert.Contains("WHERE", sql, StringComparison.Ordinal));
        Assert.Contains("\"Cliente\"", _database.Commands[0], StringComparison.Ordinal);
        Assert.Contains("\"ProtocoloAmostra\"", _database.Commands[1], StringComparison.Ordinal);
        Assert.All(_database.Commands.Take(2), sql =>
            Assert.DoesNotContain("vwDadosEnsaios", sql, StringComparison.Ordinal));
        Assert.All(_database.Commands.Skip(2), sql =>
            Assert.Contains("\"ProtocoloLactec\" IN (", sql, StringComparison.Ordinal));
        Assert.Contains("COUNT(*)", _database.Commands[2], StringComparison.Ordinal);
        Assert.Contains("GROUP BY", _database.Commands[2], StringComparison.Ordinal);
        Assert.Contains("GROUP BY", _database.Commands[3], StringComparison.Ordinal);
        Assert.Contains("LIMIT", _database.Commands[3], StringComparison.Ordinal);
        Assert.Contains("OFFSET", _database.Commands[3], StringComparison.Ordinal);
        Assert.Contains(" IN (", _database.Commands[4], StringComparison.Ordinal);
        Assert.Contains("LIMIT", _database.Commands[4], StringComparison.Ordinal);
    }

    [Fact]
    public async Task TheEquipmentHistoryIsFilteredByTenantAndKeyInTheDatabase()
    {
        _database.AddTestRow("P-1", "571289", Tenant, new DateTime(2026, 1, 1));
        _database.AddTestRow("P-1", "571289", Tenant, new DateTime(2026, 1, 1), name: "Metano", result: "3");
        _database.AddTestRow("P-2", "571289", OtherTenant, new DateTime(2026, 2, 1));
        _database.AddTestRow("P-3", "5712890", Tenant, new DateTime(2026, 3, 1));

        Page<SampleAnalysis> found = await Repository()
            .GetAnalysesOfEquipmentAsync(TestCompanies.ScopeOf(Tenant), " 571289 ", new PageRequest(1, 1), CancellationToken.None);

        SampleAnalysis analysis = Assert.Single(found.Items);
        Assert.Equal(1, found.TotalCount);
        Assert.Equal("P-1", analysis.ProtocoloLactec);
        Assert.Equal(2, analysis.Measurements.Count);
    }

    [Fact]
    public async Task AnEquipmentWithoutSerialNumberIsAddressedByItsProtocol()
    {
        _database.AddTestRow("01439/2026-004", "  ", Tenant, new DateTime(2026, 1, 1));

        Page<SampleAnalysis> found = await Repository()
            .GetAnalysesOfEquipmentAsync(TestCompanies.ScopeOf(Tenant), "01439/2026-004", new PageRequest(1, 1), CancellationToken.None);

        Assert.Equal("01439/2026-004", Assert.Single(found.Items).EquipmentKey);
    }

    [Fact]
    public async Task TheReportsOfAnEquipmentArePagedInTheDatabase()
    {
        _database.AddTestRow("P-1", "571289", Tenant, new DateTime(2026, 1, 1));
        _database.AddTestRow("P-2", "571289", Tenant, new DateTime(2026, 2, 1));
        _database.AddTestRow("P-3", "571289", Tenant, new DateTime(2026, 3, 1));
        _database.AddTestRow("P-4", "571289", OtherTenant, new DateTime(2026, 4, 1));

        AutoLabLabTestRepository repository = Repository();

        Page<SampleAnalysis> first = await repository.GetAnalysesOfEquipmentAsync(
            TestCompanies.ScopeOf(Tenant), "571289", new PageRequest(1, 2), CancellationToken.None);
        Page<SampleAnalysis> second = await repository.GetAnalysesOfEquipmentAsync(
            TestCompanies.ScopeOf(Tenant), "571289", new PageRequest(2, 2), CancellationToken.None);

        Assert.Equal(["P-3", "P-2"], first.Items.Select(item => item.ProtocoloLactec));
        Assert.True(first.HasNext);
        Assert.Equal(3, first.TotalCount);
        Assert.Equal(["P-1"], second.Items.Select(item => item.ProtocoloLactec));
        Assert.False(second.HasNext);
    }

    [Fact]
    public async Task TheProtocolOfAnotherTenantIsNotFound()
    {
        _database.AddTestRow("P-9", "5233", OtherTenant, new DateTime(2026, 1, 1));

        Assert.Null(await Repository().GetAnalysisByProtocolAsync(TestCompanies.ScopeOf(Tenant), "P-9", CancellationToken.None));
        Assert.NotNull(await Repository().GetAnalysisByProtocolAsync(TestCompanies.ScopeOf(OtherTenant), "P-9", CancellationToken.None));
    }

    [Theory]
    [InlineData(0, 1, 2, 3)]
    [InlineData(3, 2, 1, 0)]
    [InlineData(2, 0, 3, 1)]
    [InlineData(1, 3, 0, 2)]
    public void TheReportOfTheAnalysisIsTheLatestIssuedWithMeasurementsWhateverTheRowOrder(
        int first, int second, int third, int fourth)
    {
        VwDadosEnsaiosRecord[] rows =
        [
            Row("LACTEC-00002/2026", "Hidrogênio", "60", issued: new DateTime(2026, 11, 20), analysisEnd: new DateTime(2026, 11, 9)),
            Row("LACTEC-00002/2026", "Rigidez Dielétrica - Calota (kV)", "48", issued: new DateTime(2026, 11, 20), analysisEnd: new DateTime(2026, 11, 9)),
            Row("LACTEC-00003/2026", "Determinação de PCB", "2", issued: new DateTime(2026, 11, 25), analysisEnd: new DateTime(2026, 11, 12)),
            Row("LACTEC-00001/2026", "Metano", "3", issued: new DateTime(2026, 11, 10), analysisEnd: new DateTime(2026, 11, 8))
        ];

        SampleAnalysis analysis = Assert.Single(AutoLabLabTestRepository.BuildAnalyses(
            [rows[first], rows[second], rows[third], rows[fourth]]));

        Assert.Equal("LACTEC-00002/2026", analysis.Identification);
        Assert.Equal(new DateTime(2026, 11, 20), analysis.IssueDate);
        Assert.Equal(new DateTime(2026, 11, 9), analysis.AnalysisEndDate);
        Assert.Equal("571289", analysis.SerialNumber);
        Assert.Equal(3, analysis.Measurements.Count);
    }

    [Theory]
    [InlineData(false)]
    [InlineData(true)]
    public void TwoReportsIssuedOnTheSameDayAreToldApartByTheOrdinalOrderOfTheIdentification(bool reversed)
    {
        VwDadosEnsaiosRecord[] rows =
        [
            Row("LACTEC-00007/2026", "Hidrogênio", "60", issued: new DateTime(2026, 11, 20), analysisEnd: new DateTime(2026, 11, 9)),
            Row("LACTEC-00005/2026", "Metano", "3", issued: new DateTime(2026, 11, 20), analysisEnd: new DateTime(2026, 11, 10))
        ];

        SampleAnalysis analysis = Assert.Single(AutoLabLabTestRepository.BuildAnalyses(
            reversed ? rows.Reverse() : rows));

        Assert.Equal("LACTEC-00005/2026", analysis.Identification);
        Assert.Equal(new DateTime(2026, 11, 10), analysis.AnalysisEndDate);
    }

    [Fact]
    public async Task EveryReportOfTheProtocolsComesOnceWithItsIssueDateAndOnlyInsideTheTenant()
    {
        _database.AddTestRow("P-1", "571289", Tenant, new DateTime(2026, 11, 9), "LACTEC-00002/2026", issued: new DateTime(2026, 11, 20));
        _database.AddTestRow("P-1", "571289", Tenant, new DateTime(2026, 11, 9), "LACTEC-00002/2026", name: "Metano", result: "3", issued: new DateTime(2026, 11, 20));
        _database.AddTestRow("P-1", "571289", Tenant, new DateTime(2026, 11, 12), "LACTEC-00003/2026", name: "Determinação de PCB", result: "2", issued: new DateTime(2026, 11, 12));
        _database.AddTestRow("P-2", "571289", Tenant, new DateTime(2026, 5, 1), "LACTEC-00001/2026", issued: new DateTime(2026, 5, 2));
        _database.AddTestRow("P-3", "571289", Tenant, new DateTime(2026, 4, 1), "LACTEC-00009/2026");
        _database.AddTestRow("P-9", "5233", OtherTenant, new DateTime(2026, 7, 1), "LACTEC-00008/2026");
        _database.Commands.Clear();

        IReadOnlyList<ProtocolReport> reports = await Repository().GetReportsOfProtocolsAsync(
            TestCompanies.ScopeOf(Tenant), ["P-1", " P-2 ", "P-9", "P-0"], CancellationToken.None);

        Assert.Equal(
            [
                new ProtocolReport("P-1", "LACTEC-00002/2026", new DateTime(2026, 11, 20)),
                new ProtocolReport("P-1", "LACTEC-00003/2026", new DateTime(2026, 11, 12)),
                new ProtocolReport("P-2", "LACTEC-00001/2026", new DateTime(2026, 5, 2))
            ],
            reports);

        string query = Assert.Single(_database.Commands, command =>
            command.Contains("vwDadosEnsaios", StringComparison.Ordinal));
        Assert.Contains("GROUP BY", query, StringComparison.Ordinal);
        Assert.DoesNotContain("\"Resultado\"", query, StringComparison.Ordinal);
        Assert.DoesNotContain("\"Name\"", query, StringComparison.Ordinal);
    }

    [Fact]
    public async Task TheReportsOfProtocolsOutsideTheTenantNeverReachTheTestView()
    {
        _database.AddTestRow("P-9", "5233", OtherTenant, new DateTime(2026, 7, 1), "LACTEC-00008/2026");
        _database.Commands.Clear();

        IReadOnlyList<ProtocolReport> reports = await Repository().GetReportsOfProtocolsAsync(
            TestCompanies.ScopeOf(Tenant), ["P-9"], CancellationToken.None);

        Assert.Empty(reports);
        Assert.DoesNotContain(_database.Commands, command =>
            command.Contains("vwDadosEnsaios", StringComparison.Ordinal));
    }

    [Fact]
    public async Task TheReportsOfTheProtocolsRespectTheRowLimit()
    {
        for (int index = 0; index < 4; index++)
            _database.AddTestRow("P-1", "571289", Tenant, new DateTime(2026, 1, 1), $"LACTEC-0000{index}/2026");

        AutoLabLabTestRepository repository = _database.LabTests(limits: new AutoLabQueryLimits(maxRowsPerQuery: 3));

        await Assert.ThrowsAsync<LabTestSourceUnavailableException>(() => repository
            .GetReportsOfProtocolsAsync(TestCompanies.ScopeOf(Tenant), ["P-1"], CancellationToken.None));
    }

    [Fact]
    public async Task AQueryBeyondTheRowLimitFailsLoudlyInsteadOfTruncating()
    {
        for (int index = 0; index < 4; index++)
            _database.AddTestRow("P-1", "571289", Tenant, new DateTime(2026, 1, 1), name: "Metano", result: "3");

        AutoLabLabTestRepository repository = _database.LabTests(limits: new AutoLabQueryLimits(maxRowsPerQuery: 3));

        await Assert.ThrowsAsync<LabTestSourceUnavailableException>(() => repository
            .GetAnalysesOfEquipmentAsync(TestCompanies.ScopeOf(Tenant), "571289", new PageRequest(1, 1), CancellationToken.None));
    }

    [Fact]
    public async Task ABrokenDatabaseBecomesTheSourceUnavailableException()
    {
        AutoLabTestDatabase broken = new();
        AutoLabDbContext context = broken.NewContext();
        broken.Dispose();

        AutoLabLabTestRepository repository = broken.LabTests(context);

        await Assert.ThrowsAsync<LabTestSourceUnavailableException>(() => repository
            .GetAnalysesAsync(TestCompanies.ScopeOf(Tenant), null, PageRequest.First, CancellationToken.None));
    }

    private static VwDadosEnsaiosRecord Row(
        string identification,
        string name,
        string result,
        DateTime issued,
        DateTime analysisEnd)
    {
        return new VwDadosEnsaiosRecord
        {
            Identificacao = identification,
            ProtocoloLactec = "P-1",
            NumSerie = "571289",
            Name = name,
            Resultado = result,
            EmpresaTrafo = Tenant,
            DataEmissao = issued,
            DataFinalAnalise = analysisEnd
        };
    }

    private AutoLabLabTestRepository Repository()
    {
        return _database.LabTests();
    }
}
