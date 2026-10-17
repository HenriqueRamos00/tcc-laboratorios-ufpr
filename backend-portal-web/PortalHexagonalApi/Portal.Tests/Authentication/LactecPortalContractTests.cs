using System.Net;
using System.Net.Http.Json;
using System.Text.Json.Nodes;

using Microsoft.AspNetCore.Hosting;

using Portal.Tests.LabResults;

using Xunit;

namespace Portal.Tests.Authentication;

public sealed class LactecPortalContractTests
{
    [Fact]
    public async Task ModoLactecIniciaSemSpringEMantemAsRotasExistentes()
    {
        using RunningPortal basePortal = new();
        using var portal = basePortal.WithWebHostBuilder(builder =>
        {
            builder.UseSetting("Portal:DataSource", "lactec");
            builder.UseSetting("BackendLab:BaseUrl", string.Empty);
        });
        using HttpClient client = portal.CreateClient();

        using HttpResponseMessage login = await client.PostAsJsonAsync("/auth/login", new
        {
            email = "cliente@exemplo.com",
            password = "senha123"
        });

        Assert.Equal(HttpStatusCode.NotImplemented, login.StatusCode);
        JsonObject problem = JsonNode.Parse(await login.Content.ReadAsStringAsync())!.AsObject();
        Assert.Equal("authentication-not-available", (string?)problem["code"]);
        Assert.Null(problem["Token"]);

        using HttpResponseMessage quotes = await client.GetAsync("/api/quotes");
        Assert.Equal(HttpStatusCode.OK, quotes.StatusCode);

        using HttpResponseMessage equipment = await client.GetAsync("/api/equipments");
        Assert.Equal(HttpStatusCode.OK, equipment.StatusCode);
    }
}
