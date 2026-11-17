using System.Security.Cryptography;
using System.Text;

using Portal.Application.Visibility;

namespace Portal.Tests.AutoLab;

/// <summary>
/// As empresas da suite. As duas da massa de demonstracao tem o CNPJ que o Backend Auth
/// semeia; qualquer outro nome recebe um CNPJ ficticio estavel, derivado do proprio nome,
/// para que um teste possa falar de "CONCORRENTE S.A." sem combinar numero com ninguem.
/// </summary>
internal static class TestCompanies
{
    public const string Energy = "EMPRESA DE ENERGIA";
    public const string Distributor = "DISTRIBUIDORA DE ENERGIA";

    public const string EnergyCnpj = "30960572000183";
    public const string DistributorCnpj = "11222333000181";

    public static string CnpjOf(string company)
    {
        string name = company.Trim().ToUpperInvariant();

        if (name == Energy) return EnergyCnpj;
        if (name == Distributor) return DistributorCnpj;

        byte[] hash = SHA256.HashData(Encoding.UTF8.GetBytes(name));

        return string.Concat(hash.Take(14).Select(value => (char)('0' + value % 10)));
    }

    public static string Masked(string cnpj)
    {
        return $"{cnpj[..2]}.{cnpj[2..5]}.{cnpj[5..8]}/{cnpj[8..12]}-{cnpj[12..]}";
    }

    public static CompanyScope ScopeOf(params string[] companies)
    {
        return CompanyScope.OfCnpjs(companies.Select(CnpjOf));
    }

    public static bool IncludesCompany(this CompanyScope scope, string? company)
    {
        return company is not null && scope.Cnpjs.Contains(CnpjOf(company), StringComparer.Ordinal);
    }
}
