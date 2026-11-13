using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;

namespace Portal.Tests.AutoLab;

internal static class AutoLabSettings
{
    public static Dictionary<string, string?> Complete(string sourceKey)
    {
        return new Dictionary<string, string?>
        {
            [sourceKey] = "sql-server",
            ["LabResults:Scope"] = "all",
            ["AutoLab:Server"] = "servidor-de-teste,49933",
            ["AutoLab:Database"] = "bdautolab_ensaios",
            ["AutoLab:User"] = "usuario_de_teste",
            ["AutoLab:Password"] = "senha-de-teste"
        };
    }

    public static Dictionary<string, string?> WithoutDatabase(string sourceKey)
    {
        return new Dictionary<string, string?> { [sourceKey] = "sql-server", ["LabResults:Scope"] = "all" };
    }

    public static IConfiguration Configuration(Dictionary<string, string?> values)
    {
        return new ConfigurationBuilder().AddInMemoryCollection(values).Build();
    }

    public static T ResolveInScope<T>(IServiceCollection services)
        where T : notnull
    {
        ServiceProvider provider = services.BuildServiceProvider(validateScopes: true);
        IServiceScope scope = provider.CreateScope();

        return scope.ServiceProvider.GetRequiredService<T>();
    }
}
