namespace Portal.Domain.Quotes;

public sealed class Quote
{
    public string Id { get; }
    public string OpportunityId { get; }
    public string Code { get; }

    /// <summary>
    /// O codigo da cotacao como a fonte comercial o escreveu (no Salesforce,
    /// <c>Quote.Codigo_da_Cotacao__c</c>, como <c>EAQ_2024_37569_V_1</c>). Nao e o
    /// <see cref="Code"/>, que continua sendo o codigo publico do orcamento; e dele que sai a
    /// <see cref="LaboratoryKey"/>. Nulo quando a fonte nao informa.
    /// </summary>
    public string? QuotationCode { get; }

    public string Name { get; }
    public string Status { get; }
    public string Stage { get; }
    public string Description { get; }
    public string CompanyName { get; }
    public string? Protocol { get; }
    public string ExternalContactName { get; }
    public string ExternalContactEmail { get; }
    public decimal TotalPrice { get; }
    public DateTimeOffset? CreatedDate { get; }

    /// <summary>
    /// A chave pela qual o laboratorio conhece este orcamento, derivada de
    /// <see cref="QuotationCode"/>. Nula quando o codigo da cotacao esta ausente ou fora do
    /// formato: nesse caso nenhuma fonte de laboratorio e consultada por este orcamento.
    /// </summary>
    public LaboratoryQuoteKey? LaboratoryKey { get; }

    public Quote(
        string id,
        string opportunityId,
        string code,
        string name,
        string status,
        string stage,
        string description,
        string companyName,
        string externalContactName,
        string externalContactEmail,
        decimal totalPrice,
        DateTimeOffset? createdDate,
        string? protocol = null,
        string? quotationCode = null)
    {
        Id = id;
        OpportunityId = opportunityId;
        Code = code;
        QuotationCode = quotationCode;
        LaboratoryKey = LaboratoryQuoteKey.FromQuotationCode(quotationCode);
        Name = name;
        Status = status;
        Stage = stage;
        Description = description;
        CompanyName = companyName;
        Protocol = protocol;
        ExternalContactName = externalContactName;
        ExternalContactEmail = externalContactEmail;
        TotalPrice = totalPrice;
        CreatedDate = createdDate;
    }
}
