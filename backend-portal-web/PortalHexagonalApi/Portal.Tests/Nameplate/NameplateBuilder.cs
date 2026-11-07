using Portal.Domain.Nameplate;

namespace Portal.Tests.Nameplate;

/// <summary>
/// Monta placa valida com um campo trocado por vez, para o caso de teste dizer so o que
/// importa nele. Nenhum valor aqui saiu do cadastro: as TAG e os fabricantes sao inventados,
/// porque dez linhas reais carregam nome de cliente dentro de <c>Tag</c> e de
/// <c>NumSerie</c>.
/// </summary>
public static class NameplateBuilder
{
    public static EquipmentNameplate Valid(
        string serialNumber = "9000001",
        string? tag = "TR-00011",
        string? manufacturingYear = "2014",
        string? power = "111",
        string? powerUnit = "MVA",
        string? voltage = "525",
        string? manufacturer = "FABRICANTE DE REATORES",
        string? oilVolume = "23600",
        string? oilType = "Óleo Mineral Isolante",
        string? equipmentType = "REATOR")
    {
        return new EquipmentNameplate(
            serialNumber,
            tag,
            manufacturingYear,
            power is null ? null : new PowerRating(power, powerUnit),
            voltage,
            manufacturer,
            oilVolume,
            oilType,
            equipmentType);
    }

    public static EquipmentNameplate SayingOnly(string serialNumber, string? equipmentType)
    {
        return new EquipmentNameplate(
            serialNumber, null, null, null, null, null, null, null, equipmentType);
    }
}
