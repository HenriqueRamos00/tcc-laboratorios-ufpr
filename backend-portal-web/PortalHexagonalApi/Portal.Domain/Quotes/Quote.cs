namespace Portal.Domain.Quotes;

public sealed class Quote
{
    public string Id { get; }
    public string Code { get; }
    public string Name { get; }
    public string Status { get; }
    public string Stage { get; }
    public string Description { get; }
    public string CompanyName { get; }
    public string ExternalContactName { get; }
    public string ExternalContactEmail { get; }
    public decimal TotalPrice { get; }
    public DateTimeOffset? CreatedDate { get; }

    public Quote(
        string id,
        string code,
        string name,
        string status,
        string stage,
        string description,
        string companyName,
        string externalContactName,
        string externalContactEmail,
        decimal totalPrice,
        DateTimeOffset? createdDate)
    {
        Id = id;
        Code = code;
        Name = name;
        Status = status;
        Stage = stage;
        Description = description;
        CompanyName = companyName;
        ExternalContactName = externalContactName;
        ExternalContactEmail = externalContactEmail;
        TotalPrice = totalPrice;
        CreatedDate = createdDate;
    }
}
