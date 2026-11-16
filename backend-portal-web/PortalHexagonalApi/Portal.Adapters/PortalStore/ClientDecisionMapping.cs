using Portal.Domain.Quotes;

namespace Portal.Adapters.PortalStore;

public static class ClientDecisionMapping
{
    public const string DecisionSlot = "decision";
    public const string ShipmentSlot = "shipment";

    public static string SlotOf(ClientDecisionKind kind) =>
        kind == ClientDecisionKind.SampleShipment ? ShipmentSlot : DecisionSlot;

    public static ClientDecisionRow ToRow(ClientDecision decision)
    {
        ArgumentNullException.ThrowIfNull(decision);

        return new ClientDecisionRow
        {
            Id = decision.Id,
            QuoteId = decision.QuoteId,
            QuoteCode = decision.QuoteCode,
            Kind = decision.Kind.ToString(),
            Slot = SlotOf(decision.Kind),
            RecordedAt = decision.RecordedAt,
            AuthorUserId = decision.Author.UserId,
            AuthorName = decision.Author.Name,
            AuthorEmail = decision.Author.Email,
            Terms = decision.Terms is { } terms ? TermsRow(decision.Id, terms) : null,
            PurchaseOrder = decision.PurchaseOrder is { } file
                ? new PurchaseOrderRow
                {
                    DecisionId = decision.Id,
                    FileName = file.FileName,
                    SizeBytes = file.Content.Length,
                    Sha256 = file.Sha256,
                    Content = file.Content
                }
                : null,
            Rejection = decision.Justification is { } justification
                ? new RejectionRow { DecisionId = decision.Id, Justification = justification }
                : null
        };
    }

    public static ClientDecision ToSummary(ClientDecisionRow row)
    {
        ArgumentNullException.ThrowIfNull(row);

        return new ClientDecision(
            row.Id,
            row.QuoteId,
            row.QuoteCode,
            Enum.Parse<ClientDecisionKind>(row.Kind),
            new ClientDecisionAuthor(row.AuthorUserId, row.AuthorName, row.AuthorEmail),
            row.RecordedAt);
    }

    private static AcceptanceTermsRow TermsRow(Guid decisionId, AcceptanceTerms terms) => new()
    {
        DecisionId = decisionId,
        BillingCompany = terms.Billing.Company,
        BillingCnpj = terms.Billing.Cnpj,
        BillingStateRegistration = terms.Billing.StateRegistration,
        BillingStreet = terms.Billing.Street,
        BillingCity = terms.Billing.City,
        BillingState = terms.Billing.State,
        BillingDistrict = terms.Billing.District,
        BillingPostalCode = terms.Billing.PostalCode,
        BillingContact = terms.Billing.Contact,
        BillingPhone = terms.Billing.Phone,
        RequesterName = terms.Approval.RequesterName,
        ResponsibleName = terms.Approval.ResponsibleName,
        JobTitle = terms.Approval.JobTitle,
        Department = terms.Approval.Department,
        ApprovalDate = terms.Approval.Date,
        AllowsResultsByEmail = terms.Approval.AllowsResultsByEmail,
        ResultsEmail = terms.Approval.ResultsEmail,
        InvoiceEmails =
        [
            .. terms.Billing.InvoiceEmails.Select((email, index) =>
                new InvoiceEmailRow { DecisionId = decisionId, Position = index + 1, Email = email })
        ]
    };
}
