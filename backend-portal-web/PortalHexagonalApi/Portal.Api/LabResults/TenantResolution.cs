using System.Security.Claims;

namespace Portal.Api.LabResults;

/// <summary>
/// De quem e o dado que a requisicao pode ver. Mora num lugar so porque os dois controllers
/// da fatia precisam da mesma resposta, e duas copias dessa decisao e exatamente o tipo de
/// divergencia que serve o laudo de um cliente para outro quando so uma das copias e
/// corrigida.
///
/// Nao ha autenticacao configurada nesta fatia. Enquanto nao houver, o inquilino vem da
/// configuracao de desenvolvimento, e a ausencia dela nao tem queda silenciosa: quem chama
/// recebe nulo e responde 401.
/// </summary>
public static class TenantResolution
{
    public const string ClaimName = "inquilino";

    public static string? Resolve(ClaimsPrincipal? user, LabResultsOptions options)
    {
        ArgumentNullException.ThrowIfNull(options);

        string? fromClaim = user?.FindFirst(ClaimName)?.Value;

        if (!string.IsNullOrWhiteSpace(fromClaim)) return fromClaim;

        return string.IsNullOrWhiteSpace(options.DevelopmentTenant)
            ? null
            : options.DevelopmentTenant;
    }
}
