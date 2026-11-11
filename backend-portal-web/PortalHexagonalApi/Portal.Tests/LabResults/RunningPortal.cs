using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;

using Portal.Tests.AutoLab;

namespace Portal.Tests.LabResults;

public sealed class RunningPortal : WebApplicationFactory<Program>
{
    public const string SampleTenant = "EMPRESA DE ENERGIA";

    private readonly AutoLabTestDatabase _database = AutoLabTestDatabase.WithSample();

    protected override void ConfigureWebHost(IWebHostBuilder builder)
    {
        builder.UseSampleAutoLab(_database);
        builder.UseSetting("LabResults:Scope", "company");
        builder.UseSetting("LabResults:DevelopmentTenant", SampleTenant);
        builder.UseSetting("Portal:DataSource", "backend-lab");
        builder.UseSetting("BackendLab:BaseUrl", "https://backend-lab.test/");
    }

    protected override void Dispose(bool disposing)
    {
        base.Dispose(disposing);

        if (disposing) _database.Dispose();
    }
}
