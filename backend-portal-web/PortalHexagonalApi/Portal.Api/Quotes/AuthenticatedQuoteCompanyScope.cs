using Portal.Api.LabResults;
using Portal.Application.Quotes;

namespace Portal.Api.Quotes;

/// <summary>
/// O escopo comercial e o mesmo escopo do laboratorio, lido do mesmo token pelo mesmo
/// caminho: claim ausente, vazia ou com um valor malformado nao alcanca orcamento nenhum,
/// seja qual for o adaptador que responda pelos orcamentos.
/// </summary>
public sealed class AuthenticatedQuoteCompanyScope(IHttpContextAccessor context) : IQuoteCompanyScope
{
    public IReadOnlyList<string> CompanyCnpjs =>
        CompanyScopeResolution.Resolve(context.HttpContext?.User)?.Cnpjs ?? [];
}
