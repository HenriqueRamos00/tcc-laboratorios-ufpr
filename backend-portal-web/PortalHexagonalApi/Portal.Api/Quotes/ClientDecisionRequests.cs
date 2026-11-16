using System.Net.Mail;
using System.Text.RegularExpressions;

using Portal.Domain.Quotes;

namespace Portal.Api.Quotes;

public sealed record BillingAddressRequest(
    string? Company,
    string? Cnpj,
    string? StateRegistration,
    string? Street,
    string? City,
    string? State,
    string? District,
    string? PostalCode,
    string? Contact,
    string? Phone,
    string? InvoiceEmails);

public sealed record AcceptanceApprovalRequest(
    string? RequesterName,
    string? ResponsibleName,
    string? JobTitle,
    string? Department,
    DateOnly? Date,
    bool AllowsResultsByEmail,
    string? ResultsEmail);

public sealed partial record AcceptanceTermsRequest(BillingAddressRequest? Billing, AcceptanceApprovalRequest? Approval)
{
    public bool TryToTerms(out AcceptanceTerms terms, out string problem)
    {
        terms = null!;
        List<string> problems = [];

        if (Billing is null || Approval is null)
        {
            problem = "O termo precisa dos blocos billing e approval.";
            return false;
        }

        string company = Required(Billing.Company, "billing.company", FieldLimits.CompanyName, problems);
        string cnpj = Formatted(Billing.Cnpj, "billing.cnpj", CnpjFormat(), required: true, problems).ToUpperInvariant();
        string street = Required(Billing.Street, "billing.street", FieldLimits.Street, problems);
        string city = Required(Billing.City, "billing.city", FieldLimits.City, problems);
        string state = Formatted(Billing.State, "billing.state", StateFormat(), required: true, problems).ToUpperInvariant();
        string postalCode = Formatted(Billing.PostalCode, "billing.postalCode", PostalCodeFormat(), required: true, problems);
        string requesterName = Required(Approval.RequesterName, "approval.requesterName", FieldLimits.PersonName, problems);
        string responsibleName = Required(Approval.ResponsibleName, "approval.responsibleName", FieldLimits.PersonName, problems);
        string jobTitle = Required(Approval.JobTitle, "approval.jobTitle", FieldLimits.JobTitle, problems);
        string resultsEmail = Optional(Approval.ResultsEmail, "approval.resultsEmail", FieldLimits.Email, problems);

        if (Approval.Date is null) problems.Add("approval.date");
        if (Approval.AllowsResultsByEmail && resultsEmail.Length == 0) problems.Add("approval.resultsEmail");
        if (resultsEmail.Length > 0 && !MailAddress.TryCreate(resultsEmail, out _)) problems.Add("approval.resultsEmail");

        BillingAddress billing = new(
            company,
            cnpj,
            UnmaskedUpTo(Billing.StateRegistration, "billing.stateRegistration", FieldLimits.StateRegistration, problems),
            street,
            city,
            state,
            Optional(Billing.District, "billing.district", FieldLimits.District, problems),
            postalCode,
            Optional(Billing.Contact, "billing.contact", FieldLimits.PersonName, problems),
            Formatted(Billing.Phone, "billing.phone", PhoneFormat(), required: false, problems),
            Optional(Billing.InvoiceEmails, "billing.invoiceEmails", FieldLimits.InvoiceEmails, problems));

        AcceptanceApproval approval = new(
            requesterName,
            responsibleName,
            jobTitle,
            Optional(Approval.Department, "approval.department", FieldLimits.Department, problems),
            Approval.Date ?? default,
            Approval.AllowsResultsByEmail,
            resultsEmail);

        if (problems.Count > 0)
        {
            problem = "Campos ausentes ou longos demais: " + string.Join(", ", problems.Distinct()) + ".";
            return false;
        }

        terms = new AcceptanceTerms(billing, approval);
        problem = string.Empty;
        return true;
    }

    private static string Formatted(string? value, string field, Regex format, bool required, List<string> problems)
    {
        string unmasked = Unmasked(value);
        if ((required || unmasked.Length > 0) && !format.IsMatch(unmasked)) problems.Add(field);
        return unmasked;
    }

    private static string UnmaskedUpTo(string? value, string field, int maxLength, List<string> problems)
    {
        string unmasked = Unmasked(value).ToUpperInvariant();
        if (unmasked.Length > maxLength) problems.Add(field);
        return unmasked;
    }

    private static string Unmasked(string? value) =>
        string.Concat((value ?? string.Empty).Where(char.IsAsciiLetterOrDigit));

    [GeneratedRegex(@"\A[A-Za-z0-9]{12}[0-9]{2}\z", RegexOptions.CultureInvariant)]
    private static partial Regex CnpjFormat();

    [GeneratedRegex(@"\A[A-Za-z]{2}\z", RegexOptions.CultureInvariant)]
    private static partial Regex StateFormat();

    [GeneratedRegex(@"\A[0-9]{8}\z", RegexOptions.CultureInvariant)]
    private static partial Regex PostalCodeFormat();

    [GeneratedRegex(@"\A[0-9]{10,15}\z", RegexOptions.CultureInvariant)]
    private static partial Regex PhoneFormat();

    private static string Required(string? value, string field, int maxLength, List<string> problems)
    {
        string trimmed = value?.Trim() ?? string.Empty;
        if (trimmed.Length == 0 || trimmed.Length > maxLength) problems.Add(field);
        return trimmed;
    }

    private static string Optional(string? value, string field, int maxLength, List<string> problems)
    {
        string trimmed = value?.Trim() ?? string.Empty;
        if (trimmed.Length > maxLength) problems.Add(field);
        return trimmed;
    }
}

public sealed record RejectionRequest(string? Justification)
{
    public const int MaxLength = FieldLimits.Justification;
}
