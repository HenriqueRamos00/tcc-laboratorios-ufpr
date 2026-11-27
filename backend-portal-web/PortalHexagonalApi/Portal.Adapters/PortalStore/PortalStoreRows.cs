namespace Portal.Adapters.PortalStore;

public sealed class ClientDecisionRow
{
    public Guid Id { get; set; }
    public string QuoteId { get; set; } = string.Empty;
    public string QuoteCode { get; set; } = string.Empty;
    public string Kind { get; set; } = string.Empty;
    public string Slot { get; set; } = string.Empty;
    public DateTimeOffset RecordedAt { get; set; }
    public string AuthorUserId { get; set; } = string.Empty;
    public string AuthorName { get; set; } = string.Empty;
    public string AuthorEmail { get; set; } = string.Empty;
    public AcceptanceTermsRow? Terms { get; set; }
    public PurchaseOrderRow? PurchaseOrder { get; set; }
    public RejectionRow? Rejection { get; set; }
}

public sealed class AcceptanceTermsRow
{
    public Guid DecisionId { get; set; }
    public string BillingCompany { get; set; } = string.Empty;
    public string BillingCnpj { get; set; } = string.Empty;
    public string BillingStateRegistration { get; set; } = string.Empty;
    public string BillingStreet { get; set; } = string.Empty;
    public string BillingCity { get; set; } = string.Empty;
    public string BillingState { get; set; } = string.Empty;
    public string BillingDistrict { get; set; } = string.Empty;
    public string BillingPostalCode { get; set; } = string.Empty;
    public string BillingContact { get; set; } = string.Empty;
    public string BillingPhone { get; set; } = string.Empty;
    public string RequesterName { get; set; } = string.Empty;
    public string ResponsibleName { get; set; } = string.Empty;
    public string JobTitle { get; set; } = string.Empty;
    public string Department { get; set; } = string.Empty;
    public DateOnly ApprovalDate { get; set; }
    public bool AllowsResultsByEmail { get; set; }
    public string ResultsEmail { get; set; } = string.Empty;
    public List<InvoiceEmailRow> InvoiceEmails { get; set; } = [];
}

public sealed class InvoiceEmailRow
{
    public Guid DecisionId { get; set; }
    public int Position { get; set; }
    public string Email { get; set; } = string.Empty;
}

public sealed class PurchaseOrderRow
{
    public Guid DecisionId { get; set; }
    public string FileName { get; set; } = string.Empty;
    public int SizeBytes { get; set; }
    public string Sha256 { get; set; } = string.Empty;
    public byte[] Content { get; set; } = [];
}

public sealed class RejectionRow
{
    public Guid DecisionId { get; set; }
    public string Justification { get; set; } = string.Empty;
}

public sealed class ReportReadingRow
{
    public Guid SignedFileId { get; set; }
    public Guid ReportId { get; set; }
    public string Identification { get; set; } = string.Empty;
    public string Cnpj { get; set; } = string.Empty;
    public string? EquipmentKey { get; set; }
    public DateTime? IssueDate { get; set; }
    public string Outcome { get; set; } = string.Empty;
    public int ExtractorVersion { get; set; }
    public DateTime ReadAt { get; set; }
    public int Attempts { get; set; }
    public List<ReportSectionResultRow> Sections { get; set; } = [];
}

public sealed class ReportSectionResultRow
{
    public const string Chromatography = "cromatografia";
    public const string PhysicalChemical = "fisico-quimica";

    public Guid SignedFileId { get; set; }
    public string Section { get; set; } = string.Empty;
    public string? ResultLabel { get; set; }
    public string? Conformity { get; set; }
    public string? ResultColor { get; set; }
    public string? Title { get; set; }
    public string Paragraphs { get; set; } = "[]";
}

public sealed class EquipmentStatusRow
{
    public string Cnpj { get; set; } = string.Empty;
    public string EquipmentKey { get; set; } = string.Empty;
    public string? SerialNumber { get; set; }
    public string DisplayLabel { get; set; } = string.Empty;
    public string? Tag { get; set; }
    public string? EquipmentType { get; set; }
    public bool NameplateKnown { get; set; }
    public string Company { get; set; } = string.Empty;
    public string Identification { get; set; } = string.Empty;
    public int ReportCount { get; set; }
    public DateTime MostRecentReportDate { get; set; }
    public DateTime? LastReportDate { get; set; }
    public string? ChromatographyConformity { get; set; }
    public string? ChromatographyLabel { get; set; }
    public string? PhysicalChemicalConformity { get; set; }
    public string? PhysicalChemicalLabel { get; set; }
    public bool HasChromatography { get; set; }
    public bool HasPhysicalChemical { get; set; }
    public string OverallStatus { get; set; } = string.Empty;
    public DateTime UpdatedAt { get; set; }
}

public sealed class LoadScopeRow
{
    public string Cnpj { get; set; } = string.Empty;
    public bool Active { get; set; }
    public DateTime? ProcessedUntilIssueDate { get; set; }
    public Guid? ProcessedUntilReportId { get; set; }
    public DateTime? LastRunAt { get; set; }
}
