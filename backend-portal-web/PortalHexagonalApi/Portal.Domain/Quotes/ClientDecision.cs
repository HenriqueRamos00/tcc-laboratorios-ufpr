using System.Security.Cryptography;

namespace Portal.Domain.Quotes;

public enum ClientDecisionKind
{
    Acceptance,
    PurchaseOrder,
    SampleShipment,
    Rejection
}

public sealed record ClientDecisionAuthor(string UserId, string Name, string Email);

public sealed record BillingAddress(
    string Company,
    string Cnpj,
    string StateRegistration,
    string Street,
    string City,
    string State,
    string District,
    string PostalCode,
    string Contact,
    string Phone,
    string InvoiceEmails);

public sealed record AcceptanceApproval(
    string RequesterName,
    string ResponsibleName,
    string JobTitle,
    string Department,
    DateOnly Date,
    bool AllowsResultsByEmail,
    string ResultsEmail);

public sealed record AcceptanceTerms(BillingAddress Billing, AcceptanceApproval Approval);

public sealed record PurchaseOrderFile(string FileName, byte[] Content)
{
    public string Sha256 { get; } = Convert.ToHexStringLower(SHA256.HashData(Content));
}

public sealed record ClientDecision(
    Guid Id,
    string QuoteId,
    string QuoteCode,
    ClientDecisionKind Kind,
    ClientDecisionAuthor Author,
    DateTimeOffset RecordedAt,
    AcceptanceTerms? Terms = null,
    PurchaseOrderFile? PurchaseOrder = null,
    string? Justification = null);
