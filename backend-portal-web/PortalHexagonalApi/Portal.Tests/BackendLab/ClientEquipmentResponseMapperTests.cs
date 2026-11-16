using Portal.Api.LabResults;
using Portal.Application.LabResults;

using Xunit;

namespace Portal.Tests.BackendLab;

public sealed class ClientEquipmentResponseMapperTests
{
    [Fact]
    public void DetalheFormataGrandezasComDecimalPtBrSemZerosFinais()
    {
        ClientEquipment equipment = new(
            "eq-1", "SER-1", "TAG-1", "TRANSFORMADOR", "Subestação A", 13.800m,
            "Fabricante", 2021, 25.500m, "Mineral", 1200.000m, "company-1", "Empresa", 0, null);

        EquipmentDetailResponse detail = ClientEquipmentResponseMapper.Detail(equipment);

        Assert.Equal("Subestação A", detail.Location);
        Assert.Equal("Subestação A", ClientEquipmentResponseMapper.Listing(equipment).Location);
        Assert.Equal("13,8 kV", detail.Voltage);
        Assert.Equal("25,5 MVA", detail.Power);
        Assert.Equal("1200 L", detail.OilVolume);
    }
}
