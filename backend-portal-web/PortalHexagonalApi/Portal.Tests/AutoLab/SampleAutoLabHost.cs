using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.TestHost;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Infrastructure;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.DependencyInjection.Extensions;

using Portal.Adapters.AutoLab;

namespace Portal.Tests.AutoLab;

internal static class SampleAutoLabHost
{
    public static IWebHostBuilder UseSampleAutoLab(this IWebHostBuilder builder, AutoLabTestDatabase database)
    {
        builder.UseAutoLabSettings();
        builder.UseSetting("EquipmentRegistry:Source", "sql-server");
        builder.UseSetting("LaboratoryProgress:Source", "sql-server");
        builder.UseSetting("ReportDocuments:Source", "sql-server");

        return builder.ConfigureTestServices(services =>
        {
            services.RemoveAll<IDbContextOptionsConfiguration<AutoLabDbContext>>();
            services.AddDbContextPool<AutoLabDbContext>(options => database.Configure(options));
        });
    }

    public static IWebHostBuilder UseAutoLabSettings(this IWebHostBuilder builder)
    {
        builder.UseSetting("LabResults:Source", "sql-server");
        builder.UseSetting("EquipmentRegistry:Source", "not-connected");
        builder.UseSetting("LaboratoryProgress:Source", "not-connected");
        builder.UseSetting("ReportDocuments:Source", "not-connected");
        builder.UseSetting("AutoLab:Server", "banco-de-teste");
        builder.UseSetting("AutoLab:Database", "autolab");
        builder.UseSetting("AutoLab:User", "usuario_de_teste");
        builder.UseSetting("AutoLab:Password", "senha-de-teste");
        builder.UseSetting("LabResults:Scope", "all");

        return builder;
    }
}
