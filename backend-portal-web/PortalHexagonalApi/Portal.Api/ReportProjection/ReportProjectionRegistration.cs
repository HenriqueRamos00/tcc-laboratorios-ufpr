using Portal.Adapters.ReportDocuments;
using Portal.Api.Configuration;
using Portal.Api.LabResults;
using Portal.Api.PortalStore;
using Portal.Api.RealTime;
using Portal.Api.ReportDocuments;
using Portal.Application.RealTime;
using Portal.Application.ReportProjection;

namespace Portal.Api.ReportProjection;

public static class ReportProjectionRegistration
{
    public static IServiceCollection AddReportProjection(
        this IServiceCollection services,
        IConfiguration configuration)
    {
        ReportLoadOptions options = configuration
            .GetSection(ReportLoadOptions.SectionName)
            .Get<ReportLoadOptions>() ?? new ReportLoadOptions();

        string[] problems = [.. options.Problems()];

        if (problems.Length > 0)
            throw new InvalidOperationException(
                $"Configuracao '{ReportLoadOptions.SectionName}' fora dos limites: {string.Join(", ", problems)}.");

        RealTimeOptions realTime = configuration
            .GetSection(RealTimeOptions.SectionName)
            .Get<RealTimeOptions>() ?? new RealTimeOptions();

        services.AddSingleton(options);
        services.AddSingleton(realTime);
        services.AddSingleton<ReportLoadInProgress>();
        services.AddSingleton<InMemoryRealTimeChannel>();
        services.AddSingleton<IRealTimeChannel>(provider => provider.GetRequiredService<InMemoryRealTimeChannel>());
        services.AddScoped<EquipmentProjection>();
        services.AddScoped<IForConsultingEquipmentStatus, EquipmentStatusConsultation>();
        services.AddHostedService<ReportRefreshBackgroundService>();

        if (!IsConnected(configuration))
        {
            services.AddScoped<IReportLoad, UnavailableReportLoad>();
            return services;
        }

        services.AddScoped<ISignedReportSource, AutoLabSignedReportSource>();
        services.AddScoped<IReportLoad, ReportLoad>();

        if (options.Enabled)
        {
            services.AddSingleton<ReportLoadSchedule>();
            services.AddHostedService<ReportLoadBackgroundService>();
        }

        return services;
    }

    private static bool IsConnected(IConfiguration configuration)
    {
        return configuration[$"{PortalOptions.SectionName}:DataSource"] != PortalOptions.BackendLabSource
            && configuration[$"{PortalStoreOptions.SectionName}:Source"] == PortalStoreOptions.SqlServerSource
            && configuration[$"{ReportDocumentsOptions.SectionName}:Source"] == ReportDocumentsOptions.SqlServerSource
            && configuration[$"{LabResultsOptions.SectionName}:Source"] == LabResultsOptions.SqlServerSource;
    }
}
