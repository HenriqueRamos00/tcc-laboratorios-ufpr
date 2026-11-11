using Portal.Adapters.LabResults;
using Portal.Api.AutoLab;
using Portal.Application.LabResults;

namespace Portal.Api.LabResults;

/// <summary>
/// Escolhe o adaptador da porta conduzida pela configuracao, na raiz de composicao. Valor
/// ausente ou desconhecido derruba a aplicacao na inicializacao: e o unico momento em que
/// um erro de configuracao e barato de perceber.
/// </summary>
public static class LabTestSourceRegistration
{
    public static IServiceCollection AddLabTestRepository(
        this IServiceCollection services,
        IConfiguration configuration)
    {
        LabResultsOptions options = configuration.GetSection(LabResultsOptions.SectionName).Get<LabResultsOptions>()
            ?? new LabResultsOptions();

        services.AddSingleton(options);

        if (options.Source == LabResultsOptions.NotConnectedSource)
        {
            services.AddScoped<ILabTestRepository, NotConnectedLabTestRepository>();
            return services;
        }

        if (options.Scope is not (LabResultsOptions.AllScope or LabResultsOptions.CompanyScope))
            throw new InvalidOperationException(
                $"Configuracao '{LabResultsOptions.SectionName}:Scope' ausente ou com valor desconhecido "
                + $"'{options.Scope}'. Valores aceitos: '{LabResultsOptions.AllScope}', "
                + $"'{LabResultsOptions.CompanyScope}'.");

        if (string.IsNullOrWhiteSpace(options.Source))
            throw new InvalidOperationException(
                $"Configuracao '{LabResultsOptions.SectionName}:Source' nao definida. "
                + $"Valores aceitos: '{LabResultsOptions.NotConnectedSource}', '{LabResultsOptions.SqlServerSource}'.");

        if (options.Source != LabResultsOptions.SqlServerSource)
            throw new InvalidOperationException(
                $"Configuracao '{LabResultsOptions.SectionName}:Source' com valor desconhecido "
                + $"'{options.Source}'.");

        services.AddAutoLabDatabase(configuration);
        services.AddScoped<ILabTestRepository, AutoLabLabTestRepository>();

        return services;
    }
}
