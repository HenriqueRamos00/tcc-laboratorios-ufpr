using Portal.Adapters.Pipeline;
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

            case LaboratoryProgressOptions.InMemorySampleSource:
                services.AddScoped<ILaboratoryProgressRepository, InMemorySituaProcessRepository>();
                return services;

            // Quem escrever este adaptador precisa saber de duas coisas medidas nas 3345 linhas
            // do extrato, porque nenhuma delas aparece num teste em memoria. Primeira: o SQL
            // Server ignora espaco a direita na comparacao de texto, por preenchimento ANSI,
            // entao um `WHERE OrcamentoCRM = '2026_'` tambem casa '2026_   ' e o casamento
            // exato que esta fatia promete deixa de valer no banco; duas chaves do extrato
            // terminam em espaco. Segunda: duas chaves carregam o byte de controle 0x1F no
            // meio, que nao e espaco e nao e imprimivel, e o casamento exato nunca as acha,
            // que e o desfecho certo, porque limpa-las seria fundir orcamento distinto.
            case LaboratoryProgressOptions.SqlServerSource:
                throw new NotSupportedException(
                    $"A fonte '{LaboratoryProgressOptions.SqlServerSource}' ainda nao tem adaptador. "
                    + "Nao existe conexao com o SQL Server do AutoLAB.");

            default:
                throw new InvalidOperationException(
                    $"Configuracao '{LaboratoryProgressOptions.SectionName}:Source' com valor desconhecido "
                    + $"'{options.Source}'. Valores aceitos: "
                    + $"'{LaboratoryProgressOptions.NotConnectedSource}', "
                    + $"'{LaboratoryProgressOptions.InMemorySampleSource}', "
                    + $"'{LaboratoryProgressOptions.SqlServerSource}'.");
        }
    }
}
