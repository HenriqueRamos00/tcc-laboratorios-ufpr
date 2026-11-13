using Portal.Adapters.AutoLab;
using Portal.Adapters.Nameplate;
using Portal.Domain.Nameplate;

using Xunit;

namespace Portal.Tests.AutoLab;

public sealed class AutoLabEquipmentRegistryRepositoryTests : IDisposable
{
    private readonly AutoLabTestDatabase _database = new();

    public void Dispose()
    {
        _database.Dispose();
    }

    [Fact]
    public async Task OnlyTheAskedSerialNumbersAreReadInOneBoundedQuery()
    {
        _database.AddEquipment("571289", "TR-00001", "220");
        _database.AddEquipment("111.594/1", "TR-00002", "500");
        _database.AddEquipment("5233", "TR-00004", "34,5");

        IReadOnlyList<EquipmentNameplate> found = await Repository()
            .GetNameplatesOfAsync(["571289", "111.594/1"], CancellationToken.None);

        Assert.Equal(["111.594/1", "571289"], found.Select(nameplate => nameplate.SerialNumber).Order());
        string sql = Assert.Single(_database.Commands);
        Assert.Contains(" IN (", sql, StringComparison.Ordinal);
        Assert.Contains("LIMIT", sql, StringComparison.Ordinal);
    }

    [Fact]
    public async Task EveryRowOfARepeatedSerialNumberReachesTheReading()
    {
        _database.AddEquipment("9000001", "TR-00011", "525");
        _database.AddEquipment("9000001", "TR-00011", "13,8");

        EquipmentNameplate? found = await Repository()
            .GetNameplateOfAsync("9000001", CancellationToken.None);

        IReadOnlyList<EquipmentNameplate> expected = EquipmentRegistryReading.NameplatesOf(
            [
                new EquipmentRow
                {
                    NumSerie = "9000001", Tag = "TR-00011", AnoFabricacao = "2001", Potencia = "50",
                    Tensao = "525", Fabricante = "FABRICANTE", VolumeOleo = "1000", UnidadePotencia = "MVA",
                    TipoDeOleo = "Óleo Mineral Isolante", TipoEquipamento = "TRANSFORMADOR"
                },
                new EquipmentRow
                {
                    NumSerie = "9000001", Tag = "TR-00011", AnoFabricacao = "2001", Potencia = "50",
                    Tensao = "13,8", Fabricante = "FABRICANTE", VolumeOleo = "1000", UnidadePotencia = "MVA",
                    TipoDeOleo = "Óleo Mineral Isolante", TipoEquipamento = "TRANSFORMADOR"
                }
            ],
            ["9000001"]);

        EquipmentNameplate fromBothRows = Assert.Single(expected);

        Assert.NotNull(found);
        Assert.Equal(fromBothRows.Voltage, found.Voltage);
        Assert.NotEqual("525", found.Voltage);
        Assert.Equal(fromBothRows.Tag, found.Tag);
        Assert.Equal(fromBothRows.Manufacturer, found.Manufacturer);
    }

    [Fact]
    public async Task ASerialNumberThatDiffersByCaseIsNotTheSameEquipment()
    {
        _database.AddEquipment("ab-1", "TR-1", "13,8");

        Assert.Null(await Repository().GetNameplateOfAsync("AB-1", CancellationToken.None));
    }

    [Fact]
    public async Task NoSerialNumberMeansNoQuery()
    {
        Assert.Empty(await Repository().GetNameplatesOfAsync([" ", ""], CancellationToken.None));
        Assert.Empty(_database.Commands);
    }

    [Fact]
    public async Task ALongListIsSplitIntoBoundedInClauses()
    {
        _database.AddEquipment("S-1", "T", "1");
        _database.AddEquipment("S-5", "T", "1");

        AutoLabEquipmentRegistryRepository repository = new(
            _database.NewContext(),
            new AutoLabQueryLimits(maxValuesPerInClause: 2));

        IReadOnlyList<EquipmentNameplate> found = await repository.GetNameplatesOfAsync(
            ["S-1", "S-2", "S-3", "S-4", "S-5"], CancellationToken.None);

        Assert.Equal(["S-1", "S-5"], found.Select(nameplate => nameplate.SerialNumber).Order());
        Assert.Equal(3, _database.Commands.Count);
    }

    private AutoLabEquipmentRegistryRepository Repository()
    {
        return new AutoLabEquipmentRegistryRepository(_database.NewContext(), new AutoLabQueryLimits());
    }
}
