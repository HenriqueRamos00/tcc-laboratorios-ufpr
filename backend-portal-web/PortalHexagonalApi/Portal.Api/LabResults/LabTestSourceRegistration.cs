using Portal.Adapters.LabResults;
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

        if (string.IsNullOrWhiteSpace(options.Source))
            throw new InvalidOperationException(
                $"Configuracao '{LabResultsOptions.SectionName}:Source' nao definida. "
                + $"Valores aceitos: '{LabResultsOptions.EmbeddedSampleSource}', "
                + $"'{LabResultsOptions.SqlServerSource}'.");

        switch (options.Source)
        {
            case LabResultsOptions.EmbeddedSampleSource:
                services.AddScoped<ILabTestRepository, EmbeddedSampleLabTestRepository>();
                return services;

            case LabResultsOptions.SqlServerSource:
                throw new NotSupportedException(
                    $"A fonte '{LabResultsOptions.SqlServerSource}' ainda nao tem adaptador. "
                    + "Nao existe conexao com o SQL Server do AutoLAB.");

            default:
                throw new InvalidOperationException(
                    $"Configuracao '{LabResultsOptions.SectionName}:Source' com valor desconhecido "
                    + $"'{options.Source}'.");
        }
    }
}
