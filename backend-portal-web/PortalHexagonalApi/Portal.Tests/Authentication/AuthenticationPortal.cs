using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.AspNetCore.TestHost;
using Microsoft.Extensions.DependencyInjection;

using Portal.Adapters.BackendLab;
using Portal.Application.Authentication;
using Portal.Tests.AutoLab;

namespace Portal.Tests.Authentication;

internal sealed class AuthenticationPortal(HttpMessageHandler backendHandler) : WebApplicationFactory<Program>
{
    private readonly AutoLabTestDatabase _database = AutoLabTestDatabase.WithSample();

    protected override void Dispose(bool disposing)
    {
        base.Dispose(disposing);

        if (disposing) _database.Dispose();
    }

    protected override void ConfigureWebHost(IWebHostBuilder builder)
    {
        builder.UseSampleAutoLab(_database);
        builder.UseSetting("Portal:DataSource", "backend-lab");
        builder.UseSetting("BackendLab:BaseUrl", "https://backend-lab.test/");
        builder.ConfigureTestServices(services =>
        {
            services
                .AddHttpClient<IClientAuthenticator, BackendLabAdapter>()
                .ConfigurePrimaryHttpMessageHandler(() => backendHandler);
        });
    }
}
