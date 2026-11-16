namespace Portal.Application.LabResults;

/// <summary>Cadastro e resumo das publicações visíveis para o cliente autenticado.</summary>
public sealed record ClientEquipment(
    string Id,
    string? SerialNumber,
    string? Tag,
    string? Type,
    string? Substation,
    decimal? PrimaryVoltageKv,
    string? Manufacturer,
    int? ManufactureYear,
    decimal? PowerMva,
    string? OilType,
    decimal? OilVolumeLiters,
    string CompanyId,
    string CompanyName,
    int PublishedAnalysisCount,
    ClientAnalysisSummary? LatestPublishedAnalysis);
