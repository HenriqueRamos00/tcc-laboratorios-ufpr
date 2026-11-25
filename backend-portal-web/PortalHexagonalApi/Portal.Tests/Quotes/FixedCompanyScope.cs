using Portal.Adapters.Quotes;
using Portal.Application.Quotes;

namespace Portal.Tests.Quotes;

/// <summary>
/// O escopo comercial que o teste declara, no lugar do que a API le do token.
/// </summary>
public sealed class FixedCompanyScope(params string[] cnpjs) : IQuoteCompanyScope
{
    public static FixedCompanyScope DemoCompanies { get; } = new(
        FakeSalesforceQuoteRepository.EnergyCnpj, FakeSalesforceQuoteRepository.DistributorCnpj);

    public IReadOnlyList<string> CompanyCnpjs => cnpjs;
}
