using Portal.Adapters.Nameplate;
using Portal.Api.AutoLab;
using Portal.Application.Nameplate;

namespace Portal.Api.Nameplate;

/// <summary>
/// O configurador da porta conduzida do cadastro, na raiz de composicao. E o quinto elemento
/// do padrao: o unico codigo que conhece ao mesmo tempo a aplicacao e os atores conduzidos, e
/// que decide qual deles a aplicacao vai usar.
///
/// Fica em arquivo proprio, ao lado de <c>LabTestSourceRegistration</c> e de
/// <c>LaboratoryProgressSourceRegistration</c>, porque a fonte do cadastro e ortogonal a
/// fonte dos ensaios: o portal pode rodar com os laudos do AutoLAB e o cadastro desligado.
/// </summary>
public static class EquipmentRegistrySourceRegistration
{
    public static IServiceCollection AddEquipmentRegistrySource(
        this IServiceCollection services,
        IConfiguration configuration)
    {
        EquipmentRegistryOptions options = configuration
            .GetSection(EquipmentRegistryOptions.SectionName)
            .Get<EquipmentRegistryOptions>() ?? new EquipmentRegistryOptions();

        string source = string.IsNullOrWhiteSpace(options.Source)
            ? EquipmentRegistryOptions.NotConnectedSource
            : options.Source;

        switch (source)
        {
            case EquipmentRegistryOptions.NotConnectedSource:
                services.AddScoped<IEquipmentNameplateRepository, NullEquipmentNameplateRepository>();
                return services;

            case EquipmentRegistryOptions.SqlServerSource:
                services.AddAutoLabDatabase(configuration);
                services.AddScoped<IEquipmentNameplateRepository, AutoLabEquipmentRegistryRepository>();
                return services;

            default:
                throw new InvalidOperationException(
                    $"Configuracao '{EquipmentRegistryOptions.SectionName}:Source' com valor desconhecido "
                    + $"'{options.Source}'. Valores aceitos: "
                    + $"'{EquipmentRegistryOptions.NotConnectedSource}', "
                    + $"'{EquipmentRegistryOptions.SqlServerSource}'.");
        }
    }
}
