using System.Security.Claims;

using Portal.Application.Visibility;

namespace Portal.Api.LabResults;

/// <summary>
/// O escopo de empresas sai do token ja validado, e de nenhum outro lugar. Este metodo nao
/// recebe requisicao, rota, query, corpo, cabecalho nem configuracao, de proposito: o que
/// nao chega aqui nao tem como alargar o que o cliente enxerga.
///
/// Sem identidade autenticada nao ha escopo, e a rota responde 401. Identidade sem CNPJ, ou
/// com claim malformada, recebe o escopo vazio, que nao enxerga registro nenhum.
/// </summary>
public static class CompanyScopeResolution
{
    public const string CompanyCnpjsClaim = "companyCnpjs";

    public static CompanyScope? Resolve(ClaimsPrincipal? user)
    {
        if (user?.Identity is not { IsAuthenticated: true }) return null;

        return CompanyScope.OfCnpjs(user.FindAll(CompanyCnpjsClaim).Select(claim => claim.Value));
    }
}
