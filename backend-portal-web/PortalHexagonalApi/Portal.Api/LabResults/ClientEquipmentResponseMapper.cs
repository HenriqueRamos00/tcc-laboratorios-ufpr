using System.Globalization;

using Portal.Application.LabResults;

namespace Portal.Api.LabResults;

internal static class ClientEquipmentResponseMapper
{
    private static readonly TimeZoneInfo PortalTimeZone = TimeZoneInfo.FindSystemTimeZoneById("America/Sao_Paulo");

    public static EquipmentResponse Listing(ClientEquipment equipment)
    {
        string? reportCode = equipment.LatestPublishedAnalysis?.ReportCode;

        return new EquipmentResponse
        {
            Id = OpaqueIdentifier.Encode(equipment.Id),
            SerialNumber = equipment.SerialNumber,
            Tag = DisplayTag(equipment, reportCode),
            EquipmentTag = equipment.Tag,
            Type = equipment.Type,
            Location = equipment.Substation,
            Company = equipment.CompanyName,
            Identification = reportCode,
            ReportCount = equipment.PublishedAnalysisCount,
            MostRecentReportDate = PublicationDate(equipment.LatestPublishedAnalysis?.PublishedAt),
            NameplateKnown = HasRegistrationData(equipment)
        };
    }

    public static EquipmentDetailResponse Detail(ClientEquipment equipment)
    {
        string? reportCode = equipment.LatestPublishedAnalysis?.ReportCode;

        return new EquipmentDetailResponse
        {
            Id = OpaqueIdentifier.Encode(equipment.Id),
            SerialNumber = equipment.SerialNumber,
            Tag = DisplayTag(equipment, reportCode),
            EquipmentTag = equipment.Tag,
            Type = equipment.Type,
            Location = equipment.Substation,
            Company = equipment.CompanyName,
            Identification = reportCode,
            ReportCount = equipment.PublishedAnalysisCount,
            MostRecentReportDate = PublicationDate(equipment.LatestPublishedAnalysis?.PublishedAt),
            OilType = equipment.OilType,
            Voltage = QuantityWithUnit(equipment.PrimaryVoltageKv, "kV"),
            Power = QuantityWithUnit(equipment.PowerMva, "MVA"),
            Manufacturer = equipment.Manufacturer,
            ManufacturingYear = equipment.ManufactureYear?.ToString(CultureInfo.InvariantCulture),
            OilVolume = QuantityWithUnit(equipment.OilVolumeLiters, "L"),
            NameplateKnown = HasRegistrationData(equipment)
        };
    }

    public static string AnalysisDate(ClientAnalysis analysis) =>
        PublicationDate(analysis.PublishedAt) ?? throw new InvalidOperationException("A publicação não contém data.");

    private static string DisplayTag(ClientEquipment equipment, string? reportCode) =>
        equipment.Tag ?? equipment.SerialNumber ?? reportCode ?? string.Empty;

    private static string? PublicationDate(DateTimeOffset? publishedAt) => publishedAt is null
        ? null
        : WireDate.From(TimeZoneInfo.ConvertTime(publishedAt.Value, PortalTimeZone).DateTime);

    private static string? QuantityWithUnit(decimal? value, string unit) => value is null
        ? null
        : $"{value.Value.ToString("0.###", CultureInfo.GetCultureInfo("pt-BR"))} {unit}";

    private static bool HasRegistrationData(ClientEquipment equipment) =>
        equipment.Tag is not null
        || equipment.Type is not null
        || equipment.Substation is not null
        || equipment.PrimaryVoltageKv is not null
        || equipment.Manufacturer is not null
        || equipment.ManufactureYear is not null
        || equipment.PowerMva is not null
        || equipment.OilType is not null
        || equipment.OilVolumeLiters is not null;
}
