namespace Portal.Adapters.Demo;

public static class DemoCompanies
{
    public const string EnergyCnpj = "04252011000110";
    public const string DistributorCnpj = "11222333000181";
    public const string EnergyName = "EMPRESA DE ENERGIA";
    public const string DistributorName = "DISTRIBUIDORA DE ENERGIA";

    public static string? NameForCnpj(string cnpj) => cnpj switch
    {
        EnergyCnpj => EnergyName,
        DistributorCnpj => DistributorName,
        _ => null
    };
}
