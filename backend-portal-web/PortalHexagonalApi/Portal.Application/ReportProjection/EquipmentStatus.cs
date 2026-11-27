using Portal.Application.LabResults;
using Portal.Application.ReportDocuments;
using Portal.Domain.LabResults;
using Portal.Domain.Nameplate;

namespace Portal.Application.ReportProjection;

public sealed record SectionStatus(Conformity? Conformity, string? ResultLabel);

public sealed record ProjectedEquipment(
    string EquipmentKey,
    string? SerialNumber,
    string DisplayLabel,
    string? EquipmentTag,
    string? EquipmentType,
    bool NameplateKnown,
    string Company,
    string Identification,
    int ReportCount,
    DateTime MostRecentReportDate,
    IReadOnlyList<string> Identifications)
{
    public static ProjectedEquipment From(
        TestedEquipment equipment,
        EquipmentNameplate? nameplate,
        int reportCount,
        IEnumerable<ProtocolReport> reports)
    {
        ArgumentNullException.ThrowIfNull(equipment);
        ArgumentNullException.ThrowIfNull(reports);

        return new ProjectedEquipment(
            equipment.EquipmentKey,
            equipment.SerialNumber,
            equipment.DisplayLabel,
            nameplate?.Tag,
            nameplate?.EquipmentType,
            nameplate is not null,
            equipment.Company,
            equipment.MostRecentReport.Identification,
            reportCount,
            equipment.MostRecentReport.AnalysisEndDate,
            [.. ProtocolReport.FromMostRecent(reports)
                .Select(report => report.Identification)
                .Distinct(StringComparer.OrdinalIgnoreCase)]);
    }
}

public sealed record EquipmentStatus(
    string Cnpj,
    ProjectedEquipment Equipment,
    DateTime? LastReportDate,
    SectionStatus? Chromatography,
    SectionStatus? PhysicalChemical,
    OverallStatus Overall,
    DateTime UpdatedAt);

public sealed record ReadSummary(
    string Identification,
    DateTime? IssueDate,
    SectionStatus? Chromatography,
    SectionStatus? PhysicalChemical);

public static class EquipmentStatusRule
{
    public static EquipmentStatus Of(
        string cnpj,
        ProjectedEquipment equipment,
        IEnumerable<ReadSummary> summaries,
        DateTime now)
    {
        ArgumentNullException.ThrowIfNull(equipment);
        ArgumentNullException.ThrowIfNull(summaries);

        HashSet<string> identifications = new(equipment.Identifications, StringComparer.OrdinalIgnoreCase);

        List<ReadSummary> fromMostRecent = [.. summaries
            .Where(summary => identifications.Contains(summary.Identification))
            .OrderByDescending(summary => summary.IssueDate)
            .ThenBy(summary => summary.Identification, StringComparer.OrdinalIgnoreCase)];

        SectionStatus? chromatography = fromMostRecent
            .Select(summary => summary.Chromatography)
            .FirstOrDefault(section => section is not null);
        SectionStatus? physicalChemical = fromMostRecent
            .Select(summary => summary.PhysicalChemical)
            .FirstOrDefault(section => section is not null);

        if (chromatography is null && physicalChemical is null)
            return new EquipmentStatus(cnpj, equipment, null, null, null, OverallStatus.NoReport, now);

        return new EquipmentStatus(
            cnpj,
            equipment,
            fromMostRecent
                .First(summary => summary.Chromatography is not null || summary.PhysicalChemical is not null)
                .IssueDate,
            chromatography,
            physicalChemical,
            OverallStatusText.OfConformity(
                ConformityText.Worst(chromatography?.Conformity, physicalChemical?.Conformity)),
            now);
    }
}
