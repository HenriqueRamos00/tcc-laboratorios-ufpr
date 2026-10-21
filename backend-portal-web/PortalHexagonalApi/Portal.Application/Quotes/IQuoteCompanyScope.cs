namespace Portal.Application.Quotes;

public interface IQuoteCompanyScope
{
    IReadOnlyList<string> CompanyCnpjs { get; }
}
