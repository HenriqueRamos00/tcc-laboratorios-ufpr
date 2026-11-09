using System.Net;
using System.Text.Json.Nodes;

using Portal.Api.LabResults;

using Xunit;

namespace Portal.Tests.LabResults;

public sealed class ReportsContractTests : IClassFixture<RunningPortal>
{
    private readonly RunningPortal _portal;

    public ReportsContractTests(RunningPortal portal)
    {
        _portal = portal;
    }

    [Fact]
    public async Task TheListingCarriesTheFieldsOfTheContract()
    {
        JsonArray reports = await ListAsync("/api/reports");

        Assert.NotEmpty(reports);
        Assert.All(reports, report => Assert.Equal(
            ["client", "hasSignedDocument", "id", "identification", "issueDate", "publicationDate",
             "quoteCode", "requestNumber", "sampleId", "sampleName", "scheduledDate", "status"],
            report!.AsObject().Select(pair => pair.Key).Order(StringComparer.Ordinal)));
    }

    [Fact]
    public async Task TheCompanyScopeOnlyListsTheReportsOfTheCompanyMostRecentlyPublishedFirst()
    {
        HttpResponseMessage response = await _portal.CreateClient().GetAsync("/api/reports");
        JsonArray reports = JsonNode.Parse(await response.Content.ReadAsStringAsync())!.AsArray();

        Assert.Equal("9", string.Join(",", response.Headers.GetValues("X-Total-Count")));
        Assert.All(reports, report => Assert.Equal(RunningPortal.SampleTenant, (string?)report!["client"]));
        Assert.Equal("LACTEC-02354 R1/2026", (string?)reports[0]!["identification"]);
        Assert.Equal("2026-07-09", (string?)reports[0]!["publicationDate"]);
        Assert.Matches(@"^\d{4}-\d{2}-\d{2}$", (string?)reports[0]!["scheduledDate"]);
    }

    [Fact]
    public async Task TheQuoteCodeFiltersTheListing()
    {
        JsonArray reports = await ListAsync("/api/reports?quoteCode=EAQ_2026_45493_V_1");

        Assert.Equal(3, reports.Count);
        Assert.All(reports, report =>
        {
            Assert.Equal("EAQ_2026_45493_V_1", (string?)report!["quoteCode"]);
            Assert.False((bool)report["hasSignedDocument"]!);
            Assert.Null(report["publicationDate"]);
        });
    }

    [Fact]
    public async Task AnInvalidPageIsABadRequest()
    {
        HttpResponseMessage response = await _portal.CreateClient().GetAsync("/api/reports?pageSize=0");

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.Contains("invalid-page", await response.Content.ReadAsStringAsync(), StringComparison.Ordinal);
    }

    [Fact]
    public async Task EverySignedReportDownloadsAsAPdfAndTheOthersAreNotFound()
    {
        JsonArray reports = await ListAsync("/api/reports");

        foreach (JsonNode? report in reports)
        {
            HttpResponseMessage response = await _portal.CreateClient()
                .GetAsync($"/api/reports/{(string)report!["id"]!}/document");

            if ((bool)report["hasSignedDocument"]!)
            {
                response.EnsureSuccessStatusCode();
                Assert.Equal("application/pdf", response.Content.Headers.ContentType?.MediaType);
                Assert.EndsWith(".pdf", response.Content.Headers.ContentDisposition?.FileNameStar, StringComparison.Ordinal);
            }
            else
            {
                Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);
            }
        }
    }

    [Fact]
    public async Task TheReplacedRevisionDownloadsItsOwnPdf()
    {
        JsonArray reports = await ListAsync("/api/reports");
        JsonNode replaced = reports.Single(report => (string?)report!["status"] == "Substituído")!;

        HttpResponseMessage response = await _portal.CreateClient()
            .GetAsync($"/api/reports/{(string)replaced["id"]!}/document");

        Assert.Equal("LACTEC-02354 R1-2026_rev0.pdf", response.Content.Headers.ContentDisposition?.FileNameStar);
    }

    [Fact]
    public async Task AnUnreadableOrForeignReportIsRefused()
    {
        HttpResponseMessage unreadable = await _portal.CreateClient()
            .GetAsync($"/api/reports/{OpaqueIdentifier.Encode("nao-e-guid")}/document");
        HttpResponseMessage unknown = await _portal.CreateClient()
            .GetAsync($"/api/reports/{OpaqueIdentifier.Encode(Guid.NewGuid().ToString())}/document");

        Assert.Equal(HttpStatusCode.BadRequest, unreadable.StatusCode);
        Assert.Equal(HttpStatusCode.NotFound, unknown.StatusCode);
    }

    private async Task<JsonArray> ListAsync(string path)
    {
        HttpResponseMessage response = await _portal.CreateClient().GetAsync(path);
        response.EnsureSuccessStatusCode();

        return JsonNode.Parse(await response.Content.ReadAsStringAsync())!.AsArray();
    }
}
