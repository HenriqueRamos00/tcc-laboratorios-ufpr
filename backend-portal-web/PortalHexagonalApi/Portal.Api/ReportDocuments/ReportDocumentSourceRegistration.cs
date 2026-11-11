using Portal.Adapters.ReportDocuments;
using Portal.Api.AutoLab;
using Portal.Application.ReportDocuments;

namespace Portal.Api.ReportDocuments;

public static class ReportDocumentSourceRegistration
{
    public static IServiceCollection AddReportDocumentSource(
        this IServiceCollection services,
        IConfiguration configuration)
    {
        ReportDocumentsOptions options = configuration
            .GetSection(ReportDocumentsOptions.SectionName)
            .Get<ReportDocumentsOptions>() ?? new ReportDocumentsOptions();

        services.AddSingleton(options);

        string source = string.IsNullOrWhiteSpace(options.Source)
            ? ReportDocumentsOptions.NotConnectedSource
            : options.Source;

        switch (source)
        {
            case ReportDocumentsOptions.NotConnectedSource:
                services.AddScoped<IReportIssueRepository, NullReportIssueRepository>();
                return services;

            case ReportDocumentsOptions.SqlServerSource:
                services.AddAutoLabDatabase(configuration);
                services.AddScoped<IReportIssueRepository, AutoLabReportIssueRepository>();
                return services;

            default:
                throw new InvalidOperationException(
                    $"Configuracao '{ReportDocumentsOptions.SectionName}:Source' com valor desconhecido "
                    + $"'{options.Source}'. Valores aceitos: "
                    + $"'{ReportDocumentsOptions.NotConnectedSource}', "
                    + $"'{ReportDocumentsOptions.SqlServerSource}'.");
        }
    }
}
