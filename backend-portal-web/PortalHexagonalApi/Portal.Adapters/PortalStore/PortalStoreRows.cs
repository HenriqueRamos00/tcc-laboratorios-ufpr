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
