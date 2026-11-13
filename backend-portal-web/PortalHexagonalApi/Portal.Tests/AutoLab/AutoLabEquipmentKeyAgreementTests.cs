using Portal.Adapters.AutoLab;
using Portal.Adapters.LabResults;
using Portal.Adapters.Nameplate;
using Portal.Application.Paging;
using Portal.Application.Visibility;
using Portal.Domain.LabResults;
using Portal.Domain.Nameplate;

using Xunit;

namespace Portal.Tests.AutoLab;

public sealed class AutoLabEquipmentKeyAgreementTests : IDisposable
{
    private const string Tenant = "EMPRESA DE ENERGIA";

    private readonly AutoLabTestDatabase _database = new();

    public void Dispose()
    {
        _database.Dispose();
    }

    [Theory]
    [InlineData(" 571289")]
    [InlineData("571289 ")]
    [InlineData(" 571289 ")]
    public async Task AListedEquipmentWhoseSerialNumberIsPaddedInTheSourceStillOpens(string serialNumber)
    {
        _database.AddTestRow("P-1", serialNumber, Tenant, new DateTime(2026, 6, 1));

        AutoLabLabTestRepository repository = Repository();

        Page<SampleAnalysis> listing = await repository.GetAnalysesAsync(
            VisibilityScope.OfCompany(Tenant), PageRequest.First, CancellationToken.None);

        string listedKey = Assert.Single(listing.Items).EquipmentKey;

        Page<SampleAnalysis> detail = await repository.GetAnalysesOfEquipmentAsync(
            VisibilityScope.OfCompany(Tenant), listedKey, PageRequest.First, CancellationToken.None);

        Assert.Equal("571289", listedKey);
        Assert.Single(detail.Items);
        Assert.Equal(1, detail.TotalCount);
    }

    [Theory]
    [InlineData(" P-1")]
    [InlineData("P-1 ")]
    public async Task AnAnalysisFoundByProtocolAcceptsTheKeyTheListingPublished(string protocol)
    {
        _database.AddTestRow(protocol, null, Tenant, new DateTime(2026, 6, 1));

        AutoLabLabTestRepository repository = Repository();

        Page<SampleAnalysis> listing = await repository.GetAnalysesAsync(
            VisibilityScope.OfCompany(Tenant), PageRequest.First, CancellationToken.None);

        string listedKey = Assert.Single(listing.Items).EquipmentKey;

        SampleAnalysis? found = await repository.GetAnalysisByProtocolAsync(
            VisibilityScope.OfCompany(Tenant), listedKey, CancellationToken.None);

        Assert.Equal("P-1", listedKey);
        Assert.NotNull(found);
        Assert.Equal("P-1", found.ProtocoloLactec);
    }

    [Theory]
    [InlineData(" 571289")]
    [InlineData("571289 ")]
    public async Task ANameplateWhoseSerialNumberIsPaddedInTheSourceIsStillFound(string serialNumber)
    {
        _database.AddEquipment(serialNumber, "TAG-1", "138");

        AutoLabEquipmentRegistryRepository registry = new(
            _database.NewContext(), new AutoLabQueryLimits());

        EquipmentNameplate? found = await registry.GetNameplateOfAsync(
            "571289", CancellationToken.None);

        Assert.NotNull(found);
        Assert.Equal("571289", found.SerialNumber);
        Assert.Equal("138", found.Voltage);
    }

    private AutoLabLabTestRepository Repository()
    {
        return new AutoLabLabTestRepository(_database.NewContext(), new AutoLabQueryLimits());
    }
}
