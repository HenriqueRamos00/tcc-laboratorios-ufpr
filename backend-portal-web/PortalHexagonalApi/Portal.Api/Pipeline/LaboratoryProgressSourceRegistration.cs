using Portal.Adapters.Pipeline;
using Portal.Api.AutoLab;
using Portal.Application.Pipeline;

namespace Portal.Api.Pipeline;

/// <summary>
/// O configurador da porta conduzida de etapas, na raiz de composicao. E o quinto elemento
/// do padrao: o unico codigo que conhece ao mesmo tempo a aplicacao e os atores conduzidos,
/// e que decide qual deles a aplicacao vai usar.
///
/// Fica em arquivo proprio, ao lado de <c>LabTestSourceRegistration</c>, e nao dentro de
/// <c>PortalSourceRegistration</c>, porque a fonte da etapa e ortogonal a fonte do
/// orcamento: o portal pode rodar com orcamento do Salesforce e etapa desligada, e e assim
/// que ele roda hoje.
/// </summary>
public static class LaboratoryProgressSourceRegistration
{
    public static IServiceCollection AddLaboratoryProgressSource(
        this IServiceCollection services,
        IConfiguration configuration)
    {
        LaboratoryProgressOptions options = configuration
            .GetSection(LaboratoryProgressOptions.SectionName)
            .Get<LaboratoryProgressOptions>() ?? new LaboratoryProgressOptions();

        string source = string.IsNullOrWhiteSpace(options.Source)
            ? LaboratoryProgressOptions.NotConnectedSource
            : options.Source;

        switch (source)
        {
            case LaboratoryProgressOptions.NotConnectedSource:
                services.AddScoped<ILaboratoryProgressRepository, NullLaboratoryProgressRepository>();
                return services;

            case LaboratoryProgressOptions.SqlServerSource:
                services.AddAutoLabDatabase(configuration);
                services.AddScoped<ILaboratoryProgressRepository, AutoLabSituaProcessRepository>();
                return services;

            default:
                throw new InvalidOperationException(
                    $"Configuracao '{LaboratoryProgressOptions.SectionName}:Source' com valor desconhecido "
                    + $"'{options.Source}'. Valores aceitos: "
                    + $"'{LaboratoryProgressOptions.NotConnectedSource}', "
                    + $"'{LaboratoryProgressOptions.SqlServerSource}'.");
        }
    }
}
