using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.AspNetCore.TestHost;
using Microsoft.Extensions.DependencyInjection;

using Portal.Adapters.BackendLab;
using Portal.Application.Authentication;

namespace Portal.Tests.Authentication;

internal sealed class AuthenticationPortal(HttpMessageHandler backendHandler) : WebApplicationFactory<Program>
{
    protected override void ConfigureWebHost(IWebHostBuilder builder)
    {
        builder.UseSetting("LabResults:Source", "embedded-sample");
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
