using System.Net;
using System.Net.Http.Headers;
using System.Text;
using System.Text.Json.Nodes;

using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.AspNetCore.TestHost;
using Microsoft.AspNetCore.WebUtilities;
using Microsoft.Extensions.DependencyInjection;

using Portal.Adapters.BackendLab;
using Portal.Application.LabResults;

using Xunit;

namespace Portal.Tests.BackendLab;

public sealed class SpringEquipmentContractTests
{
    [Theory]
    [InlineData(null, null)]
    [InlineData("   ", null)]
    [InlineData("  TAG + %_/[1]&other=value  ", "TAG + %_/[1]&other=value")]
    public async Task ListingForwardsSearchAndPagingToSpringWithTheClientBearer(
        string? text, string? expectedSearch)
    {
        using Origin origin = new();
        using SpringPortal portal = new(origin);
        using HttpClient client = portal.CreateClient();
        client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", "client-session");
        string url = "/api/equipments?page=2&pageSize=1";
        if (text is not null) url += "&search=" + Uri.EscapeDataString(text);

        using HttpResponseMessage response = await client.GetAsync(url);

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.Equal("2", Assert.Single(response.Headers.GetValues("X-Page")));
        Assert.Equal("1", Assert.Single(response.Headers.GetValues("X-Page-Size")));
        Assert.Equal("3", Assert.Single(response.Headers.GetValues("X-Total-Count")));
        JsonNode equipment = JsonNode.Parse(await response.Content.ReadAsStringAsync())!.AsArray().Single()!;
        Assert.Equal("Subestação A", (string?)equipment["location"]);
        Assert.Equal("TAG-1", (string?)equipment["equipmentTag"]);
        Assert.Null(equipment["mostRecentReportDate"]);

        Uri request = Assert.Single(origin.Requests);
        Assert.Equal("/api/client/equipments", request.AbsolutePath);
        var query = QueryHelpers.ParseQuery(request.Query);
        Assert.Equal("2", query["page"].ToString());
        Assert.Equal("1", query["pageSize"].ToString());
        if (expectedSearch is null) Assert.False(query.ContainsKey("search"));
        else Assert.Equal(expectedSearch, query["search"].ToString());
        Assert.Equal(expectedSearch is null ? 2 : 3, query.Count);
        Assert.Equal("Bearer client-session", Assert.Single(origin.Authorizations));
    }

    [Fact]
    public async Task OverlongSearchIsRejectedBeforeReadingSpring()
    {
        using Origin origin = new();
        using SpringPortal portal = new(origin);
        using HttpClient client = portal.CreateClient();
        client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", "client-session");

        using HttpResponseMessage response = await client.GetAsync("/api/equipments?search=" + new string('x', 101));

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        JsonNode problem = JsonNode.Parse(await response.Content.ReadAsStringAsync())!;
        Assert.Equal("invalid-search", (string?)problem["code"]);
        Assert.Empty(origin.Requests);
    }

    private sealed class SpringPortal(Origin origin) : WebApplicationFactory<Program>
    {
        protected override void ConfigureWebHost(IWebHostBuilder builder)
        {
            builder.UseSetting("Portal:DataSource", "backend-lab");
            builder.UseSetting("BackendLab:BaseUrl", "https://spring.test/");
            builder.UseSetting("LabResults:Source", "backend-lab");
            builder.UseSetting("EquipmentRegistry:Source", "backend-lab");
            builder.UseSetting("LaboratoryProgress:Source", "not-connected");
            builder.UseSetting("ReportDocuments:Source", "not-connected");
            builder.UseSetting("LabResults:Scope", "");
            builder.UseSetting("AutoLab:Server", "");
            builder.ConfigureTestServices(services =>
                services.AddHttpClient<IClientLabResultsRepository, BackendLabClientLabResultsRepository>()
                    .ConfigurePrimaryHttpMessageHandler(() => origin));
        }
    }

    private sealed class Origin : HttpMessageHandler
    {
        public List<Uri> Requests { get; } = [];
        public List<string?> Authorizations { get; } = [];

        protected override Task<HttpResponseMessage> SendAsync(
            HttpRequestMessage request, CancellationToken cancellationToken)
        {
            Requests.Add(request.RequestUri!);
            Authorizations.Add(request.Headers.Authorization?.ToString());
            return Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent("""
                    {"items":[{"id":"eq-1","company":{"id":"company-1","name":"Empresa"},
                    "serialNumber":"S-1","tag":"TAG-1","type":"TRANSFORMADOR",
                    "substation":"Subestação A","publishedAnalysisCount":0,"latestPublishedAnalysis":null}],
                    "page":2,"pageSize":1,"totalItems":3}
                    """, Encoding.UTF8, "application/json")
            });
        }
    }
}
