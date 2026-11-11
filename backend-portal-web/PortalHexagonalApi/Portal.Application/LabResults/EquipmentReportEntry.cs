using Portal.Domain.LabResults;

namespace Portal.Application.LabResults;

public sealed record EquipmentReportEntry(EquipmentReport Report, bool HasSignedDocument);
