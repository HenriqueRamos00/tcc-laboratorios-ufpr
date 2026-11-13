using System.Net;
using System.Text.Json.Nodes;

using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.AspNetCore.TestHost;
using Microsoft.Extensions.DependencyInjection;

using Portal.Api.LabResults;
using Portal.Application.Paging;
using Portal.Application.ReportDocuments;
using Portal.Application.ReportDocuments.Exceptions;
using Portal.Application.Visibility;

using Xunit;

namespace Portal.Tests.LabResults;

public sealed class PagingAndSignedReportContractTests : IClassFixture<RunningPortal>
{
    private static readonly byte[] Pdf = [0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x37];

    private readonly RunningPortal _portal;

    public PagingAndSignedReportContractTests(RunningPortal portal)
    {
        _portal = portal;
    }

    [Theory]
    [InlineData("?pageSize=0", true)]
    [InlineData("?pageSize=201", true)]
    [InlineData("?page=0", true)]
    [InlineData("?page=-1", true)]
    [InlineData("?page=abc", false)]
    [InlineData("?page=1.5", false)]
    public async Task AnInvalidPageIsABadRequestWithProblemDetails(string query, bool outOfRange)
    {
        HttpResponseMessage listing = await _portal.CreateClient().GetAsync($"/api/equipments{query}");
        HttpResponseMessage reports = await _portal.CreateClient()
            .GetAsync($"/api/equipments/{OpaqueIdentifier.Encode("571289")}/reports{query}");

        Assert.Equal(HttpStatusCode.BadRequest, listing.StatusCode);
        Assert.Equal(HttpStatusCode.BadRequest, reports.StatusCode);
        Assert.Equal("application/problem+json", listing.Content.Headers.ContentType?.MediaType);

        if (outOfRange)
            Assert.Contains("invalid-page", await listing.Content.ReadAsStringAsync(), StringComparison.Ordinal);
    }

    [Fact]
    public async Task TheListingStaysAnArrayAndCarriesThePageInTheHeaders()
    {
        HttpResponseMessage all = await _portal.CreateClient().GetAsync("/api/equipments");
        int total = JsonNode.Parse(await all.Content.ReadAsStringAsync())!.AsArray().Count;

        HttpResponseMessage first = await _portal.CreateClient().GetAsync("/api/equipments?pageSize=1");

        first.EnsureSuccessStatusCode();

        JsonArray firstPage = JsonNode.Parse(await first.Content.ReadAsStringAsync())!.AsArray();

        Assert.Single(firstPage);
        Assert.Equal(total.ToString(System.Globalization.CultureInfo.InvariantCulture), Header(first, "X-Total-Count"));
        Assert.Equal("1", Header(first, "X-Page"));
        Assert.Equal("1", Header(first, "X-Page-Size"));
        Assert.True(total > 1);

        HttpResponseMessage second = await _portal.CreateClient().GetAsync("/api/equipments?page=2&pageSize=1");
        JsonArray secondPage = JsonNode.Parse(await second.Content.ReadAsStringAsync())!.AsArray();

        Assert.Single(secondPage);
        Assert.NotEqual((string?)firstPage[0]!["id"], (string?)secondPage[0]!["id"]);
    }

    [Fact]
    public async Task WithoutParametersTheListingIsTheFirstPageOfFifty()
    {
        HttpResponseMessage response = await _portal.CreateClient().GetAsync("/api/equipments");

        response.EnsureSuccessStatusCode();

        JsonArray page = JsonNode.Parse(await response.Content.ReadAsStringAsync())!.AsArray();

        Assert.InRange(page.Count, 1, 50);
        Assert.Equal("1", Header(response, "X-Page"));
        Assert.Equal("50", Header(response, "X-Page-Size"));
    }

    [Fact]
    public async Task ThePagingHeadersAreExposedToTheBrowser()
    {
        using HttpRequestMessage request = new(HttpMethod.Get, "/api/equipments");
        request.Headers.Add("Origin", "http://localhost:4200");

        HttpResponseMessage response = await _portal.CreateClient().SendAsync(request);
        string exposed = Header(response, "Access-Control-Expose-Headers");

        Assert.Contains("X-Total-Count", exposed, StringComparison.OrdinalIgnoreCase);
        Assert.Contains("X-Page", exposed, StringComparison.OrdinalIgnoreCase);
        Assert.Contains("X-Page-Size", exposed, StringComparison.OrdinalIgnoreCase);
        Assert.Contains("Content-Disposition", exposed, StringComparison.OrdinalIgnoreCase);
    }

    [Fact]
    public async Task TheReportsOfAnEquipmentArePaged()
    {
        string equipment = OpaqueIdentifier.Encode("571289");

        HttpResponseMessage first = await _portal.CreateClient()
            .GetAsync($"/api/equipments/{equipment}/reports?pageSize=3");
        HttpResponseMessage second = await _portal.CreateClient()
            .GetAsync($"/api/equipments/{equipment}/reports?page=2&pageSize=3");

        JsonArray firstPage = JsonNode.Parse(await first.Content.ReadAsStringAsync())!.AsArray();
        JsonArray secondPage = JsonNode.Parse(await second.Content.ReadAsStringAsync())!.AsArray();

        Assert.Equal(3, firstPage.Count);
        Assert.Equal("4", Header(first, "X-Total-Count"));
        Assert.Single(secondPage);
        Assert.Equal("2", Header(second, "X-Page"));
    }

    [Fact]
    public async Task TheReportsOfAnotherTenantsEquipmentAreNotFound()
    {
        HttpResponseMessage response = await _portal.CreateClient()
            .GetAsync($"/api/equipments/{OpaqueIdentifier.Encode("5233")}/reports");

        Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);
    }

    [Fact]
    public async Task AReportWithoutASignedPdfIsNotFound()
    {
        string equipment = OpaqueIdentifier.Encode("884120");
        string report = OpaqueIdentifier.Encode("01439/2026-004");

        HttpResponseMessage response = await _portal.CreateClient()
            .GetAsync($"/api/equipments/{equipment}/reports/{report}/document");

        Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);
        Assert.Contains("report-document-not-found", await response.Content.ReadAsStringAsync(), StringComparison.Ordinal);
    }

    [Fact]
    public async Task TheSignedReportOfTheSampleIsServedWithItsStoredFileName()
    {
        HttpClient client = _portal.CreateClient();

        HttpResponseMessage response = await client.GetAsync(await FirstReportDocumentRouteAsync(client));

        response.EnsureSuccessStatusCode();
        Assert.Equal("application/pdf", response.Content.Headers.ContentType?.MediaType);
        Assert.Equal("LACTEC-02272-2026_rev0.pdf", response.Content.Headers.ContentDisposition?.FileNameStar);
        Assert.StartsWith("%PDF-", System.Text.Encoding.Latin1.GetString(await response.Content.ReadAsByteArrayAsync())[..5], StringComparison.Ordinal);
    }

    [Fact]
    public async Task TheSignedReportIsServedAsAPdfWithTheStoredFileName()
    {
        RecordingDocuments documents = new(new SignedReportDocument("C:\\laudos\\LACTEC-1.pdf", Pdf));
        using WebApplicationFactory<Program> portal = WithDocuments(documents);
        using HttpClient client = portal.CreateClient();

        HttpResponseMessage response = await client.GetAsync(await FirstReportDocumentRouteAsync(client));

        response.EnsureSuccessStatusCode();
        Assert.Equal("application/pdf", response.Content.Headers.ContentType?.MediaType);
        Assert.Equal("LACTEC-1.pdf", response.Content.Headers.ContentDisposition?.FileNameStar);
        Assert.Equal(Pdf, await response.Content.ReadAsByteArrayAsync());
        Assert.Single(documents.IdentificationsAsked);
    }

    [Fact]
    public async Task TheReportOfAnotherTenantIsNotFoundAndTheDocumentSourceIsNeverAsked()
    {
        RecordingDocuments documents = new(new SignedReportDocument("x.pdf", Pdf));
        using WebApplicationFactory<Program> portal = WithDocuments(documents);
        using HttpClient client = portal.CreateClient();

        string ownEquipment = OpaqueIdentifier.Encode("571289");
        string reportOfAnotherOwnEquipment = OpaqueIdentifier.Encode("01351/2026-004");
        string foreignReport = OpaqueIdentifier.Encode("01563/2026-018");
        string foreignEquipment = OpaqueIdentifier.Encode("5233");

        HttpResponseMessage crossed = await client
            .GetAsync($"/api/equipments/{ownEquipment}/reports/{foreignReport}/document");
        HttpResponseMessage foreign = await client
            .GetAsync($"/api/equipments/{foreignEquipment}/reports/{foreignReport}/document");
        HttpResponseMessage sideways = await client
            .GetAsync($"/api/equipments/{ownEquipment}/reports/{reportOfAnotherOwnEquipment}/document");

        Assert.Equal(HttpStatusCode.NotFound, crossed.StatusCode);
        Assert.Equal(HttpStatusCode.NotFound, foreign.StatusCode);
        Assert.Equal(HttpStatusCode.NotFound, sideways.StatusCode);
        Assert.Empty(documents.IdentificationsAsked);
    }

    [Fact]
    public async Task AnUnreadableReportIdentifierIsABadRequest()
    {
        HttpResponseMessage response = await _portal.CreateClient()
            .GetAsync($"/api/equipments/{OpaqueIdentifier.Encode("571289")}/reports/%25%25/document");

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
    }

    [Fact]
    public async Task TheFallOfTheDocumentSourceIsABadGateway()
    {
        RecordingDocuments documents = new(null)
        {
            Failure = new ReportDocumentSourceUnavailableException("queda")
        };
        using WebApplicationFactory<Program> portal = WithDocuments(documents);
        using HttpClient client = portal.CreateClient();

        HttpResponseMessage response = await client.GetAsync(await FirstReportDocumentRouteAsync(client));

        Assert.Equal(HttpStatusCode.BadGateway, response.StatusCode);
        Assert.DoesNotContain("queda", await response.Content.ReadAsStringAsync(), StringComparison.Ordinal);
    }

    private static string Header(HttpResponseMessage response, string name)
    {
        return string.Join(",", response.Headers.GetValues(name));
    }

    private WebApplicationFactory<Program> WithDocuments(RecordingDocuments documents)
    {
        return _portal.WithWebHostBuilder(builder => builder.ConfigureTestServices(services =>
            services.AddScoped<IReportIssueRepository>(_ => documents)));
    }

    private static async Task<string> FirstReportDocumentRouteAsync(HttpClient client)
    {
        string equipment = OpaqueIdentifier.Encode("571289");

        HttpResponseMessage response = await client.GetAsync($"/api/equipments/{equipment}/reports");
        JsonArray reports = JsonNode.Parse(await response.Content.ReadAsStringAsync())!.AsArray();

        return $"/api/equipments/{equipment}/reports/{(string)reports[0]!["id"]!}/document";
    }

    private sealed class RecordingDocuments : IReportIssueRepository
    {
        private readonly SignedReportDocument? _document;

        public RecordingDocuments(SignedReportDocument? document)
        {
            _document = document;
        }

        public ReportDocumentSourceUnavailableException? Failure { get; init; }

        public List<string> IdentificationsAsked { get; } = [];

        public Task<SignedReportDocument?> GetSignedDocumentOfAsync(
            string reportIdentification,
            CancellationToken cancellationToken)
        {
            IdentificationsAsked.Add(reportIdentification);

            if (Failure is not null) throw Failure;

            return Task.FromResult(_document);
        }

        public Task<IReadOnlySet<string>> GetIdentificationsWithSignedDocumentAsync(
            IReadOnlyCollection<string> reportIdentifications,
            CancellationToken cancellationToken)
        {
            return Task.FromResult<IReadOnlySet<string>>(new HashSet<string>(reportIdentifications));
        }

        public Task<ReportIssue?> GetCurrentIssueOfAsync(
            string reportIdentification,
            CancellationToken cancellationToken)
        {
            return Task.FromResult<ReportIssue?>(null);
        }

        public Task<Page<LabReport>> ListReportsAsync(
            VisibilityScope scope,
            string? quoteCode,
            PageRequest page,
            CancellationToken cancellationToken)
        {
            return Task.FromResult(Page<LabReport>.Empty(page));
        }

        public Task<SignedReportDocument?> GetSignedDocumentOfReportAsync(
            VisibilityScope scope,
            Guid reportId,
            CancellationToken cancellationToken)
        {
            return Task.FromResult<SignedReportDocument?>(null);
        }
    }
}
