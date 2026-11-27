using System.Net;
using System.Net.Http.Json;
using System.Text.Json.Nodes;

using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.AspNetCore.TestHost;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;

using Portal.Adapters.PortalStore;
using Portal.Adapters.ReportDocuments;
using Portal.Api.LabResults;
using Portal.Api.RealTime;
using Portal.Application.ReportProjection;
using Portal.Tests.Authentication;
using Portal.Tests.AutoLab;
using Portal.Tests.LabResults;
using Portal.Tests.ReportDocuments;

using Xunit;

namespace Portal.Tests.ReportProjection;

public sealed class PortalWithProjection : WebApplicationFactory<Program>
{
    public const string Red = "LACTEC-00001/2026";
    public const string Green = "LACTEC-00002/2026";

    private readonly AutoLabTestDatabase _autoLab = new();
    private readonly ProjectionTestStore _store = new();

    public PortalWithProjection()
    {
        _autoLab.AddTestRow("01/2026-001", "571289", TestCompanies.Energy, new DateTime(2026, 9, 20), Red);
        _autoLab.AddTestRow("02/2026-001", "TR-2", TestCompanies.Energy, new DateTime(2026, 9, 21), Green);
        _autoLab.AddTestRow("09/2026-001", "DX-1", TestCompanies.Distributor, new DateTime(2026, 9, 23), "LACTEC-00009/2026");

        _autoLab.AddReportIssue(
            Red,
            _autoLab.AddFile("vermelho.pdf", SyntheticReportPdf.Of("Descarga elétrica", null, gasFill: CellFill.Red)),
            published: new DateTime(2026, 9, 24));
        _autoLab.AddReportIssue(
            Green,
            _autoLab.AddFile("verde.pdf", SyntheticReportPdf.Of("NORMAL", null, gasFill: CellFill.Green)),
            published: new DateTime(2026, 9, 25));
    }

    internal ProjectionTestStore Store => _store;

    protected override void ConfigureWebHost(IWebHostBuilder builder)
    {
        builder.UseSampleAutoLab(_autoLab);
        builder.UseLactecSessions();
        builder.UseSetting("ReportLoad:PauseBetweenPdfsMilliseconds", "0");

        builder.ConfigureTestServices(services =>
        {
            services.AddDbContext<PortalStoreDbContext>(options => options.UseSqlite(_store.Connection));
            services.AddScoped<IReportProjectionStore, SqlServerReportProjectionStore>();
            services.AddScoped<ISignedReportSource, AutoLabSignedReportSource>();
            services.AddScoped<IReportLoad, ReportLoad>();
        });
    }

    protected override void Dispose(bool disposing)
    {
        base.Dispose(disposing);

        if (!disposing) return;

        _autoLab.Dispose();
        _store.Dispose();
    }
}

public sealed class EquipmentStatusContractTests : IClassFixture<PortalWithProjection>
{
    private readonly PortalWithProjection _portal;

    public EquipmentStatusContractTests(PortalWithProjection portal)
    {
        _portal = portal;
    }

    [Fact]
    public async Task TheWholeWalkFromTheButtonToTheBadgeAndTheFilter()
    {
        using HttpClient client = _portal.CreateClientOf(TestCompanies.EnergyCnpj);
        string equipment = OpaqueIdentifier.Encode("571289");

        JsonArray before = await ListAsync(client, "/api/equipments");
        Assert.Equal(2, before.Count);
        Assert.Null(RowOf(before, equipment)["status"]);
        Assert.Equal(HttpStatusCode.NoContent, (await client.GetAsync($"/api/equipments/{equipment}/status")).StatusCode);

        HttpResponseMessage refreshed = await client.PostAsync($"/api/equipments/{equipment}/status/refresh", null);

        Assert.Equal(HttpStatusCode.OK, refreshed.StatusCode);
        JsonObject body = JsonNode.Parse(await refreshed.Content.ReadAsStringAsync())!.AsObject();
        Assert.Equal(
            ["alreadyRunning", "reportsRead", "reportsUnreadable", "status", "success"],
            body.Select(pair => pair.Key).Order());
        Assert.False((bool)body["alreadyRunning"]!);
        Assert.True((bool)body["success"]!);
        Assert.Equal(1, (int)body["reportsRead"]!);
        Assert.Equal("nao-conforme", (string?)body["status"]!["overall"]);

        JsonObject status = RowOf(await ListAsync(client, "/api/equipments"), equipment)["status"]!.AsObject();
        Assert.Equal(
            ["chromatography", "lastReportDate", "overall", "physicalChemical", "updatedAt"],
            status.Select(pair => pair.Key).Order());
        Assert.Equal("nao-conforme", (string?)status["overall"]);
        Assert.Equal("nao-conforme", (string?)status["chromatography"]!["conformity"]);
        Assert.Equal("Descarga elétrica", (string?)status["chromatography"]!["resultLabel"]);
        Assert.Null(status["physicalChemical"]);
        Assert.Equal("2026-09-24", (string?)status["lastReportDate"]);

        JsonObject alone = JsonNode.Parse(
            await client.GetStringAsync($"/api/equipments/{equipment}/status"))!.AsObject();
        Assert.Equal("nao-conforme", (string?)alone["overall"]);

        HttpResponseMessage filtered = await client.GetAsync("/api/equipments?status=nao-conforme&page=1&pageSize=10");
        JsonArray rows = JsonNode.Parse(await filtered.Content.ReadAsStringAsync())!.AsArray();

        Assert.Equal("1", string.Join(",", filtered.Headers.GetValues(PageQuery.TotalCountHeader)));
        JsonObject row = Assert.Single(rows)!.AsObject();
        Assert.Equal(equipment, (string?)row["id"]);
        Assert.Equal("571289", (string?)row["serialNumber"]);
        Assert.Equal(PortalWithProjection.Red, (string?)row["identification"]);
        Assert.Equal(1, (int)row["reportCount"]!);
        Assert.Equal("nao-conforme", (string?)row["status"]!["overall"]);
        Assert.Empty(await ListAsync(client, "/api/equipments?status=alerta"));
        Assert.Empty(await ListAsync(client, "/api/equipments?status=nao-conforme&search=nada"));
    }

    [Fact]
    public async Task AnotherCompanyNeitherRefreshesNorSeesTheStatusOfTheEquipment()
    {
        using HttpClient owner = _portal.CreateClientOf(TestCompanies.EnergyCnpj);
        using HttpClient stranger = _portal.CreateClientOf(TestCompanies.DistributorCnpj);
        string equipment = OpaqueIdentifier.Encode("TR-2");

        HttpResponseMessage refused = await stranger.PostAsync($"/api/equipments/{equipment}/status/refresh", null);

        Assert.Equal(HttpStatusCode.NotFound, refused.StatusCode);
        Assert.DoesNotContain(_portal.Store.Statuses(), row => row.EquipmentKey == "TR-2");

        Assert.Equal(HttpStatusCode.OK, (await owner.PostAsync($"/api/equipments/{equipment}/status/refresh", null)).StatusCode);

        Assert.Equal(HttpStatusCode.NoContent, (await stranger.GetAsync($"/api/equipments/{equipment}/status")).StatusCode);
        Assert.Empty(await ListAsync(stranger, "/api/equipments?status=normal"));
        Assert.Single(await ListAsync(owner, "/api/equipments?status=normal"));
    }

    [Fact]
    public async Task TheRefreshNeedsASessionAndAReadableIdentifier()
    {
        using HttpClient anonymous = _portal.CreateClientWithToken(null);
        using HttpClient client = _portal.CreateClientOf(TestCompanies.EnergyCnpj);

        Assert.Equal(
            HttpStatusCode.Unauthorized,
            (await anonymous.PostAsync($"/api/equipments/{OpaqueIdentifier.Encode("571289")}/status/refresh", null)).StatusCode);
        Assert.Equal(
            HttpStatusCode.BadRequest,
            (await client.PostAsync("/api/equipments/***/status/refresh", null)).StatusCode);
        Assert.Equal(
            HttpStatusCode.NotFound,
            (await client.PostAsync($"/api/equipments/{OpaqueIdentifier.Encode("nao-existe")}/status/refresh", null)).StatusCode);
    }

    [Fact]
    public async Task AnUnknownStatusIsRefusedBeforeAnyQuery()
    {
        using HttpClient client = _portal.CreateClientOf(TestCompanies.EnergyCnpj);

        HttpResponseMessage response = await client.GetAsync("/api/equipments?status=vermelho");

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.Contains("invalid-equipment-status", await response.Content.ReadAsStringAsync(), StringComparison.Ordinal);
    }

    [Fact]
    public async Task WhoIsWaitingReceivesTheEndOfTheRoundAndNobodyElseDoes()
    {
        using HttpClient client = _portal.CreateClientOf(TestCompanies.EnergyCnpj);
        using CancellationTokenSource limit = new(TimeSpan.FromSeconds(20));

        using HttpResponseMessage response = await client.GetAsync(
            "/api/events", HttpCompletionOption.ResponseHeadersRead, limit.Token);

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.Equal("text/event-stream", response.Content.Headers.ContentType?.MediaType);

        InMemoryRealTimeChannel channel = _portal.Services.GetRequiredService<InMemoryRealTimeChannel>();
        channel.Publish("carga.concluida", """{"status":"success","reportsRead":0}""", new HashSet<string> { "outra-pessoa" });
        channel.Publish("carga.concluida", """{"status":"success","reportsRead":7}""", new HashSet<string> { "42" });

        using StreamReader reader = new(await response.Content.ReadAsStreamAsync(limit.Token));
        List<string> lines = [];

        while (!lines.Any(line => line.Contains("\"reportsRead\":7", StringComparison.Ordinal))
            && await reader.ReadLineAsync(limit.Token) is string line)
        {
            if (line.Length > 0) lines.Add(line);
        }

        Assert.Equal("event: ping", lines[0]);

        Assert.Contains("event: carga.concluida", lines);
        Assert.Contains("""data: {"status":"success","reportsRead":7}""", lines);
        Assert.DoesNotContain(lines, line => line.Contains("\"reportsRead\":0", StringComparison.Ordinal));
    }

    private static async Task<JsonArray> ListAsync(HttpClient client, string route)
    {
        HttpResponseMessage response = await client.GetAsync(route);

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);

        return JsonNode.Parse(await response.Content.ReadAsStringAsync())!.AsArray();
    }

    private static JsonObject RowOf(JsonArray list, string id)
    {
        return list.Single(row => (string?)row!["id"] == id)!.AsObject();
    }
}

public sealed class EquipmentStatusBatchContractTests : IClassFixture<PortalWithProjection>
{
    private readonly PortalWithProjection _portal;

    public EquipmentStatusBatchContractTests(PortalWithProjection portal)
    {
        _portal = portal;
    }

    [Fact]
    public async Task TheVisibleEquipmentIsAcceptedAndProjectedBehindTheAnswer()
    {
        using HttpClient client = _portal.CreateClientOf(TestCompanies.DistributorCnpj);
        string own = OpaqueIdentifier.Encode("DX-1");
        string foreign = OpaqueIdentifier.Encode("TR-2");

        HttpResponseMessage response = await client.PostAsJsonAsync(
            "/api/equipments/status/refresh", new { ids = new[] { own, foreign } });

        Assert.Equal(HttpStatusCode.Accepted, response.StatusCode);
        JsonObject body = JsonNode.Parse(await response.Content.ReadAsStringAsync())!.AsObject();
        Assert.Equal(["accepted", "alreadyRunning"], body.Select(pair => pair.Key).Order());
        Assert.Equal(2, (int)body["accepted"]!);

        await WaitForAsync(() => _portal.Store.Statuses().Any(row => row.EquipmentKey == "DX-1"));
        await WaitForAsync(() => !_portal.Services.GetRequiredService<ReportLoadInProgress>().IsRunning);

        Assert.DoesNotContain(_portal.Store.Statuses(), row => row.Cnpj != TestCompanies.DistributorCnpj
            && row.EquipmentKey == "DX-1");
        Assert.DoesNotContain(_portal.Store.Statuses(), row => row.Cnpj == TestCompanies.DistributorCnpj
            && row.EquipmentKey == "TR-2");
        Assert.Equal(HttpStatusCode.OK, (await client.GetAsync($"/api/equipments/{own}/status")).StatusCode);
        Assert.Equal(HttpStatusCode.NoContent, (await client.GetAsync($"/api/equipments/{foreign}/status")).StatusCode);

        HttpResponseMessage repeated = await client.PostAsJsonAsync(
            "/api/equipments/status/refresh", new { ids = new[] { own } });

        Assert.Equal(HttpStatusCode.Accepted, repeated.StatusCode);
        Assert.Equal(0, (int)JsonNode.Parse(await repeated.Content.ReadAsStringAsync())!["accepted"]!);
    }

    [Fact]
    public async Task TheBatchHasACeilingAndNeedsReadableIdentifiersAndASession()
    {
        using HttpClient anonymous = _portal.CreateClientWithToken(null);
        using HttpClient client = _portal.CreateClientOf(TestCompanies.DistributorCnpj);
        string[] tooMany = [.. Enumerable.Range(0, 101).Select(index => OpaqueIdentifier.Encode($"EQ-{index}"))];

        HttpResponseMessage over = await client.PostAsJsonAsync("/api/equipments/status/refresh", new { ids = tooMany });
        HttpResponseMessage empty = await client.PostAsJsonAsync(
            "/api/equipments/status/refresh", new { ids = Array.Empty<string>() });
        HttpResponseMessage unreadable = await client.PostAsJsonAsync(
            "/api/equipments/status/refresh", new { ids = new[] { "***" } });

        Assert.Equal(HttpStatusCode.BadRequest, over.StatusCode);
        Assert.Contains("too-many-equipment", await over.Content.ReadAsStringAsync(), StringComparison.Ordinal);
        Assert.Equal(HttpStatusCode.BadRequest, empty.StatusCode);
        Assert.Equal(HttpStatusCode.BadRequest, unreadable.StatusCode);
        Assert.Contains("invalid-identifier", await unreadable.Content.ReadAsStringAsync(), StringComparison.Ordinal);
        Assert.Equal(
            HttpStatusCode.Unauthorized,
            (await anonymous.PostAsJsonAsync("/api/equipments/status/refresh", new { ids = tooMany })).StatusCode);
        Assert.Equal(
            HttpStatusCode.Unauthorized,
            (await anonymous.PostAsync("/api/equipments/status/sync", null)).StatusCode);
    }

    [Fact]
    public async Task TheRestOfTheClientIsAcceptedAndLoadedOnlyForTheCompaniesOfTheSession()
    {
        using HttpClient client = _portal.CreateClientOf(TestCompanies.EnergyCnpj);

        HttpResponseMessage response = await client.PostAsync("/api/equipments/status/sync", null);

        Assert.Equal(HttpStatusCode.Accepted, response.StatusCode);
        JsonObject body = JsonNode.Parse(await response.Content.ReadAsStringAsync())!.AsObject();
        Assert.Equal(["accepted", "alreadyRunning"], body.Select(pair => pair.Key).Order());
        Assert.Equal(1, (int)body["accepted"]!);

        await WaitForAsync(() => _portal.Store.Statuses().Count(row => row.Cnpj == TestCompanies.EnergyCnpj) == 2);

        Assert.Equal(
            [("571289", "nao-conforme"), ("TR-2", "normal")],
            _portal.Store.Statuses()
                .Where(row => row.Cnpj == TestCompanies.EnergyCnpj)
                .Select(row => (row.EquipmentKey, row.OverallStatus)));
        Assert.All(_portal.Store.Readings(), row => Assert.Equal(TestCompanies.EnergyCnpj, row.Cnpj));
    }

    private static async Task WaitForAsync(Func<bool> reached)
    {
        using CancellationTokenSource limit = new(TimeSpan.FromSeconds(20));

        while (!reached()) await Task.Delay(50, limit.Token);
    }
}

public sealed class EquipmentStatusWithoutProjectionTests : IClassFixture<RunningPortal>
{
    private readonly RunningPortal _portal;

    public EquipmentStatusWithoutProjectionTests(RunningPortal portal)
    {
        _portal = portal;
    }

    [Fact]
    public async Task WithoutThePortalStoreTheRefreshDoesNotExistAndTheListKeepsWorking()
    {
        using HttpClient client = _portal.CreateClient();
        string equipment = OpaqueIdentifier.Encode("571289");

        HttpResponseMessage refresh = await client.PostAsync($"/api/equipments/{equipment}/status/refresh", null);

        Assert.Equal(HttpStatusCode.NotFound, refresh.StatusCode);
        Assert.Contains("equipment-status-unavailable", await refresh.Content.ReadAsStringAsync(), StringComparison.Ordinal);

        HttpResponseMessage batch = await client.PostAsJsonAsync(
            "/api/equipments/status/refresh", new { ids = new[] { equipment } });
        HttpResponseMessage sync = await client.PostAsync("/api/equipments/status/sync", null);

        Assert.Equal(HttpStatusCode.NotFound, batch.StatusCode);
        Assert.Contains("equipment-status-unavailable", await batch.Content.ReadAsStringAsync(), StringComparison.Ordinal);
        Assert.Equal(HttpStatusCode.NotFound, sync.StatusCode);

        JsonArray list = JsonNode.Parse(await client.GetStringAsync("/api/equipments"))!.AsArray();

        Assert.NotEmpty(list);
        Assert.All(list, row => Assert.Null(row!["status"]));
        Assert.Empty(JsonNode.Parse(await client.GetStringAsync("/api/equipments?status=normal"))!.AsArray());
    }
}
