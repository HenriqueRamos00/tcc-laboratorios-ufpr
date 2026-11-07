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
            ["company", "equipmentTag", "id", "identification", "location",
             "mostRecentReportDate", "nameplateKnown", "reportCount", "serialNumber",
             "tag", "type"],
            equipment!.AsObject().Select(pair => pair.Key).Order()));
    }

    /// <summary>
    /// Localizacao nao existe no cadastro: sao dez colunas e nenhuma delas e lugar. A chave
    /// tem que continuar no corpo mesmo nula, em toda linha: sem ela a tela imprime undefined
    /// na coluna em vez de dizer que o dado nao e informado.
    /// </summary>
    [Fact]
    public async Task LocationIsNullAndPresentInEveryRow()
    {
        JsonArray list = await ListAsync();

        Assert.All(list, equipment =>
        {
            Assert.True(equipment!.AsObject().ContainsKey("location"));
            Assert.Null(equipment["location"]);
        });
    }

    /// <summary>
    /// O tipo de equipamento deixou de ser sempre nulo e sai como o cadastro escreve,
    /// acentuado e sem fundir com o par quase igual. A serie 111.594/1 e uma das quatro da
    /// amostra que o cadastro conhece.
    /// </summary>
    [Fact]
    public async Task TheEquipmentTypeComesFromTheRegistryWrittenAsTheRegistryWroteIt()
    {
        JsonArray list = await ListAsync();

        JsonObject known = OneWithSerialNumber(list, "111.594/1");

        Assert.True((bool?)known["nameplateKnown"]);
        Assert.Equal("TRANSFORMADOR DE POTÊNCIA", (string?)known["type"]);
        Assert.Equal("TR-00002", (string?)known["equipmentTag"]);
    }

    /// <summary>
    /// As duas series que este projeto inventou nao existem no cadastro, e o corpo diz isso
    /// em vez de inventar placa. E a confirmacao independente de que a juncao e pelo numero
    /// de serie de verdade: ela acerta em todo dado verdadeiro e falha so onde o numero e
    /// nosso.
    /// </summary>
    [Fact]
    public async Task AnInventedSerialNumberHasNoNameplateAndSaysSo()
    {
        JsonArray list = await ListAsync();

        JsonObject unknown = OneWithSerialNumber(list, "884120");

        Assert.False((bool?)unknown["nameplateKnown"]);
        Assert.Null(unknown["type"]);
        Assert.Null(unknown["equipmentTag"]);
    }

    /// <summary>
    /// O cadastro nao tem coluna de empresa, e a serie 5233 esta nele com placa completa. O
    /// equipamento e de outro cliente e continua inalcancavel, porque o cadastro so e
    /// consultado depois de as analises do inquilino responderem. A placa daquele cliente nao
    /// aparece em lugar nenhum do corpo servido a este.
    /// </summary>
    [Fact]
    public async Task TheRegistryDoesNotBecomeAWayIntoAnotherTenantsEquipment()
    {
        HttpResponseMessage detail = await _portal.CreateClient()
            .GetAsync($"/api/equipments/{OpaqueIdentifier.Encode("5233")}");

        Assert.Equal(HttpStatusCode.NotFound, detail.StatusCode);

        JsonArray list = await ListAsync();

        Assert.DoesNotContain(
            "TR-00004",
            list.Select(equipment => (string?)equipment!["equipmentTag"]));
    }

    /// <summary>
    /// O detalhe carrega os tres campos novos do cadastro, e a potencia vem com a unidade que
    /// o cadastro escreveu. A listagem nao carrega nenhum dos tres: ela nao os imprime, e
    /// mandar nove campos de placa em toda linha e peso sem leitor.
    /// </summary>
    [Fact]
    public async Task TheDetailCarriesTheNameplateFieldsTheListingDoesNot()
    {
        JsonArray list = await ListAsync();
        string identifier = (string)OneWithSerialNumber(list, "571289")["id"]!;

        HttpResponseMessage response = await _portal.CreateClient()
            .GetAsync($"/api/equipments/{identifier}");

        response.EnsureSuccessStatusCode();

        JsonObject detail = JsonNode.Parse(await response.Content.ReadAsStringAsync())!.AsObject();

        Assert.Equal("50 MVA", (string?)detail["power"]);
        Assert.Equal("220", (string?)detail["voltage"]);
        Assert.Equal("FABRICANTE DE TRANSFORMADORES", (string?)detail["manufacturer"]);
        Assert.Equal("1979", (string?)detail["manufacturingYear"]);
        Assert.Equal("20570", (string?)detail["oilVolume"]);
        Assert.False(list[0]!.AsObject().ContainsKey("power"));
        Assert.False(list[0]!.AsObject().ContainsKey("manufacturer"));
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

    /// <summary>
    /// A linha da listagem de uma serie. Existe para o caso de teste dizer so o que importa
    /// nele, e porque a listagem cresceu: tres casos desta classe precisam da mesma travessia.
    /// </summary>
    private static JsonObject OneWithSerialNumber(JsonArray list, string serialNumber)
    {
        return list
            .OfType<JsonNode>()
            .Select(equipment => equipment.AsObject())
            .Single(equipment => (string?)equipment["serialNumber"] == serialNumber);
    }

    private async Task<JsonArray> ListAsync()
    {
        HttpResponseMessage response = await _portal.CreateClient().GetAsync("/api/equipments");
        response.EnsureSuccessStatusCode();

        return JsonNode.Parse(await response.Content.ReadAsStringAsync())!.AsArray();
    }
}
