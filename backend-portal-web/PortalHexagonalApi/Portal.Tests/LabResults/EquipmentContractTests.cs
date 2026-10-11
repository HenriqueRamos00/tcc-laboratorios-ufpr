using System.Net;
using System.Text.Json.Nodes;

using Portal.Api.LabResults;

using Xunit;

namespace Portal.Tests.LabResults;

/// <summary>
/// O contrato da listagem, capturado do corpo HTTP real. A afirmacao que mais importa aqui
/// nao e a forma de um campo: e o passeio inteiro, porque a tela de equipamentos so serve
/// para chegar na de indicadores. Um identificador que a lista devolve e a tela de dentro
/// nao aceita derruba o fluxo sem erro nenhum na borda.
/// </summary>
public sealed class EquipmentContractTests : IClassFixture<RunningPortal>
{
    private readonly RunningPortal _portal;

    public EquipmentContractTests(RunningPortal portal)
    {
        _portal = portal;
    }

    [Fact]
    public async Task TheListingCarriesTheFieldsTheScreenDeclares()
    {
        JsonArray list = await ListAsync();

        Assert.NotEmpty(list);
        Assert.All(list, equipment => Assert.Equal(
            ["company", "id", "identification", "location", "mostRecentReportDate",
             "reportCount", "serialNumber", "tag", "type"],
            equipment!.AsObject().Select(pair => pair.Key).Order()));
    }

    /// <summary>
    /// Tipo e localizacao nao existem no AutoLAB. A chave tem que continuar no corpo mesmo
    /// nula: sem ela a tela imprime undefined na coluna em vez de dizer que o laboratorio
    /// nao informa esse dado.
    /// </summary>
    [Fact]
    public async Task AColumnAutoLabDoesNotHaveArrivesNullAndPresent()
    {
        JsonObject first = (await ListAsync())[0]!.AsObject();

        Assert.True(first.ContainsKey("type"));
        Assert.True(first.ContainsKey("location"));
        Assert.Null(first["type"]);
        Assert.Null(first["location"]);
    }

    [Fact]
    public async Task TheListingOnlyCarriesTheTenantsEquipment()
    {
        JsonArray list = await ListAsync();

        Assert.All(list, equipment =>
            Assert.Equal(RunningPortal.SampleTenant, (string?)equipment!["company"]));

        IEnumerable<string?> labels = list.Select(equipment => (string?)equipment!["tag"]);

        Assert.Contains("571289", labels);
        Assert.Contains("111.594/1", labels);
        Assert.DoesNotContain("5233", labels);
        Assert.DoesNotContain("36170301", labels);
    }

    /// <summary>
    /// Todo equipamento que a listagem publica abre pelo identificador que ela mesma
    /// publicou, nas tres rotas que o usam. E o defeito que ja aconteceu e que nenhum teste
    /// de um equipamento especifico pega: a listagem somava um laudo que respondia 404 ao ser
    /// aberto, porque a rota exigia numero de serie e aquele laudo nao tinha.
    ///
    /// O caso e varrido em vez de escrito a mao de proposito: assim ele continua valendo
    /// quando a amostra mudar, que e exatamente o que acabou de acontecer com o numero de
    /// serie provisorio.
    /// </summary>
    [Fact]
    public async Task EveryListedEquipmentOpensByTheIdentifierTheListingPublished()
    {
        JsonArray list = await ListAsync();

        Assert.NotEmpty(list);

        foreach (JsonNode? equipment in list)
        {
            string identifier = (string?)equipment!["id"]
                ?? throw new InvalidOperationException("Equipamento listado sem identificador.");

            foreach (string route in new[] { string.Empty, "/reports", "/health" })
            {
                HttpResponseMessage response = await _portal.CreateClient()
                    .GetAsync($"/api/equipments/{identifier}{route}");

                Assert.True(
                    response.IsSuccessStatusCode,
                    $"/api/equipments/{{id}}{route} respondeu {(int)response.StatusCode} "
                    + $"para a TAG '{(string?)equipment["tag"]}'.");
            }
        }
    }

    [Fact]
    public async Task EquipmentWithAHistoryCountsEveryReport()
    {
        JsonArray list = await ListAsync();

        JsonNode withHistory = list.Single(equipment => (string?)equipment!["tag"] == "571289");

        Assert.Equal(4, (int?)withHistory["reportCount"]);

        HttpResponseMessage response = await _portal.CreateClient()
            .GetAsync($"/api/equipments/{(string?)withHistory["id"]}/reports");

        response.EnsureSuccessStatusCode();

        JsonArray reports = JsonNode.Parse(await response.Content.ReadAsStringAsync())!.AsArray();

        Assert.Equal(4, reports.Count);
    }

    /// <summary>
    /// O passeio da tela: a lista devolve um identificador, e e com ele que a tela de
    /// indicadores e pedida. Este teste e o que falha se os dois lados pararem de falar da
    /// mesma chave de equipamento.
    /// </summary>
    [Fact]
    public async Task AnIdentifierFromTheListingOpensTheIndicatorsScreen()
    {
        JsonArray list = await ListAsync();

        foreach (JsonNode? equipment in list)
        {
            string identifier = (string?)equipment!["id"]
                ?? throw new InvalidOperationException("Listed equipment without an identifier.");

            HttpResponseMessage response = await _portal.CreateClient()
                .GetAsync($"/api/equipments/{identifier}/health");

            Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        }
    }

    /// <summary>
    /// A continuacao do passeio: cada linha do historico leva a sua propria analise. O
    /// identificador do laudo carrega o protocolo, que tem barra, e so sobrevive na rota
    /// porque viaja opaco.
    /// </summary>
    [Fact]
    public async Task AReportIdentifierOpensTheSelectedAnalysis()
    {
        JsonArray list = await ListAsync();
        string equipment = (string?)list.Single(item => (string?)item!["tag"] == "571289")["id"]!;

        HttpResponseMessage reportsResponse = await _portal.CreateClient()
            .GetAsync($"/api/equipments/{equipment}/reports");

        reportsResponse.EnsureSuccessStatusCode();

        JsonArray reports = JsonNode.Parse(await reportsResponse.Content.ReadAsStringAsync())!.AsArray();

        foreach (JsonNode? report in reports)
        {
            HttpResponseMessage response = await _portal.CreateClient()
                .GetAsync($"/api/equipments/{equipment}/health/reports/{(string?)report!["id"]}");

            response.EnsureSuccessStatusCode();

            JsonObject body = JsonNode.Parse(await response.Content.ReadAsStringAsync())!.AsObject();

            Assert.NotNull(body["selectedAnalysis"]);
            Assert.Equal((string?)report["date"], (string?)body["selectedAnalysis"]!["date"]);
        }
    }

    [Fact]
    public async Task TheAnalysisTypeAndStatusTravelAsTextTheScreenRecognizes()
    {
        JsonArray list = await ListAsync();
        string equipment = (string?)list.Single(item => (string?)item!["tag"] == "571289")["id"]!;

        HttpResponseMessage response = await _portal.CreateClient()
            .GetAsync($"/api/equipments/{equipment}/reports");

        response.EnsureSuccessStatusCode();

        JsonArray reports = JsonNode.Parse(await response.Content.ReadAsStringAsync())!.AsArray();

        Assert.All(reports, report =>
        {
            Assert.Equal("chromatography", (string?)report!["analysisType"]);
            Assert.Equal("completed", (string?)report["status"]);
            Assert.Null(report["reportUrl"]);
        });
    }

    [Fact]
    public async Task AnotherTenantsEquipmentIsNotReachable()
    {
        string ofAnotherCustomer = OpaqueIdentifier.Encode("5233");
        HttpClient client = _portal.CreateClient();

        HttpResponseMessage detail = await client.GetAsync($"/api/equipments/{ofAnotherCustomer}");
        HttpResponseMessage reports = await client.GetAsync($"/api/equipments/{ofAnotherCustomer}/reports");

        Assert.Equal(HttpStatusCode.NotFound, detail.StatusCode);
        Assert.Equal(HttpStatusCode.NotFound, reports.StatusCode);
    }

    [Fact]
    public async Task AnUnreadableIdentifierIsRefusedInsteadOfNotFound()
    {
        HttpResponseMessage response = await _portal.CreateClient()
            .GetAsync("/api/equipments/nao-e-base-64-%2F%2F");

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
    }

    private async Task<JsonArray> ListAsync()
    {
        HttpResponseMessage response = await _portal.CreateClient().GetAsync("/api/equipments");
        response.EnsureSuccessStatusCode();

        return JsonNode.Parse(await response.Content.ReadAsStringAsync())!.AsArray();
    }
}
