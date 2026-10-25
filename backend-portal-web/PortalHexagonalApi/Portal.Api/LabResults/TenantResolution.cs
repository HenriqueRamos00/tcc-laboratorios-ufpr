using System.Security.Claims;
using Portal.Adapters.Demo;
using Portal.Api.Configuration;

namespace Portal.Api.LabResults;

/// <summary>
/// De quem e o dado que a requisicao pode ver. Mora num lugar so porque os dois controllers
/// da fatia precisam da mesma resposta, e duas copias dessa decisao e exatamente o tipo de
/// divergencia que serve o laudo de um cliente para outro quando so uma das copias e
/// corrigida.
///
/// Na demonstracao autenticada, o CNPJ assinado escolhe a empresa da amostra embutida.
/// Os demais modos conservam a resolucao de inquilino existente.
/// </summary>
public static class TenantResolution
{
    public const string ClaimName = "inquilino";

    public static string? Resolve(ClaimsPrincipal? user, LabResultsOptions options, PortalOptions portal)
    {
        ArgumentNullException.ThrowIfNull(options);

        if (portal.UseDemoData && portal.DataSource == PortalOptions.LactecSource)
        {
            if (user?.Identity?.IsAuthenticated != true) return null;
            string[] cnpjs = user.FindAll("companyCnpjs").Select(claim => claim.Value)
                .Distinct(StringComparer.Ordinal).ToArray();
            // shortcut: cada cliente demo tem uma empresa; ampliar antes de demonstrar clientes multiempresa.
            return cnpjs.Length == 1 ? DemoCompanies.NameForCnpj(cnpjs[0]) : null;
        }

        string? fromClaim = user?.FindFirst(ClaimName)?.Value;

        if (!string.IsNullOrWhiteSpace(fromClaim)) return fromClaim;

        return string.IsNullOrWhiteSpace(options.DevelopmentTenant)
            ? null
            : options.DevelopmentTenant;
    }
}
