using Portal.Adapters.AutoLab;
using Portal.Adapters.LabResults;
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

    [Fact]
    public async Task TheTenantListingOnlyBringsTheTenantsEquipmentAndIgnoresCase()
    {
        _database.AddTestRow("P-1", "571289", Tenant, new DateTime(2026, 6, 1));
        _database.AddTestRow("P-2", "111.594/1", "empresa de energia", new DateTime(2026, 5, 1));
        _database.AddTestRow("P-3", "5233", OtherTenant, new DateTime(2026, 7, 1));

        Page<SampleAnalysis> page = await Repository()
            .GetAnalysesAsync(VisibilityScope.OfCompany(Tenant), PageRequest.First, CancellationToken.None);

        Assert.Equal(["111.594/1", "571289"], page.Items.Select(item => item.EquipmentKey).Order());
        Assert.False(page.HasNext);
        Assert.Equal(2, page.TotalCount);
        Assert.DoesNotContain(page.Items, item => item.EquipmentKey == "5233");
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
            .GetAnalysesAsync(VisibilityScope.OfCompany(Tenant), new PageRequest(1, 2), CancellationToken.None);
        Page<SampleAnalysis> second = await repository
            .GetAnalysesAsync(VisibilityScope.OfCompany(Tenant), new PageRequest(2, 2), CancellationToken.None);
        Page<SampleAnalysis> third = await repository
            .GetAnalysesAsync(VisibilityScope.OfCompany(Tenant), new PageRequest(3, 2), CancellationToken.None);

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

        await Repository().GetAnalysesAsync(VisibilityScope.OfCompany(Tenant), new PageRequest(1, 10), CancellationToken.None);

        Assert.Equal(3, _database.Commands.Count);
        Assert.All(_database.Commands, sql => Assert.Contains("WHERE", sql, StringComparison.Ordinal));
        Assert.Contains("COUNT(*)", _database.Commands[0], StringComparison.Ordinal);
        Assert.Contains("GROUP BY", _database.Commands[0], StringComparison.Ordinal);
        Assert.Contains("GROUP BY", _database.Commands[1], StringComparison.Ordinal);
        Assert.Contains("LIMIT", _database.Commands[1], StringComparison.Ordinal);
        Assert.Contains("OFFSET", _database.Commands[1], StringComparison.Ordinal);
        Assert.Contains(" IN (", _database.Commands[2], StringComparison.Ordinal);
        Assert.Contains("LIMIT", _database.Commands[2], StringComparison.Ordinal);
    }

    [Fact]
    public async Task TheEquipmentHistoryIsFilteredByTenantAndKeyInTheDatabase()
    {
        _database.AddTestRow("P-1", "571289", Tenant, new DateTime(2026, 1, 1));
        _database.AddTestRow("P-1", "571289", Tenant, new DateTime(2026, 1, 1), name: "Metano", result: "3");
        _database.AddTestRow("P-2", "571289", OtherTenant, new DateTime(2026, 2, 1));
        _database.AddTestRow("P-3", "5712890", Tenant, new DateTime(2026, 3, 1));

        Page<SampleAnalysis> found = await Repository()
            .GetAnalysesOfEquipmentAsync(VisibilityScope.OfCompany(Tenant), " 571289 ", new PageRequest(1, 1), CancellationToken.None);

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
            .GetAnalysesOfEquipmentAsync(VisibilityScope.OfCompany(Tenant), "01439/2026-004", new PageRequest(1, 1), CancellationToken.None);

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
            VisibilityScope.OfCompany(Tenant), "571289", new PageRequest(1, 2), CancellationToken.None);
        Page<SampleAnalysis> second = await repository.GetAnalysesOfEquipmentAsync(
            VisibilityScope.OfCompany(Tenant), "571289", new PageRequest(2, 2), CancellationToken.None);

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

        Assert.Null(await Repository().GetAnalysisByProtocolAsync(VisibilityScope.OfCompany(Tenant), "P-9", CancellationToken.None));
        Assert.NotNull(await Repository().GetAnalysisByProtocolAsync(VisibilityScope.OfCompany(OtherTenant), "P-9", CancellationToken.None));
    }

    [Fact]
    public async Task AQueryBeyondTheRowLimitFailsLoudlyInsteadOfTruncating()
    {
        for (int index = 0; index < 4; index++)
            _database.AddTestRow("P-1", "571289", Tenant, new DateTime(2026, 1, 1), name: "Metano", result: "3");

        AutoLabLabTestRepository repository = new(_database.NewContext(), new AutoLabQueryLimits(maxRowsPerQuery: 3));

        await Assert.ThrowsAsync<LabTestSourceUnavailableException>(() => repository
            .GetAnalysesOfEquipmentAsync(VisibilityScope.OfCompany(Tenant), "571289", new PageRequest(1, 1), CancellationToken.None));
    }

    [Fact]
    public async Task ABrokenDatabaseBecomesTheSourceUnavailableException()
    {
        AutoLabTestDatabase broken = new();
        AutoLabDbContext context = broken.NewContext();
        broken.Dispose();

        AutoLabLabTestRepository repository = new(context, new AutoLabQueryLimits());

        await Assert.ThrowsAsync<LabTestSourceUnavailableException>(() => repository
            .GetAnalysesAsync(VisibilityScope.OfCompany(Tenant), PageRequest.First, CancellationToken.None));
    }

    private AutoLabLabTestRepository Repository()
    {
        return new AutoLabLabTestRepository(_database.NewContext(), new AutoLabQueryLimits());
    }
}
