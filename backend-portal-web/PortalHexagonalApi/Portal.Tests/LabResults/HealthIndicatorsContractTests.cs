using System.Net;
using System.Text.Json;
using System.Text.Json.Nodes;

using Portal.Api.LabResults;

using Xunit;

namespace Portal.Tests.LabResults;

/// <summary>
/// O contrato capturado do corpo HTTP real. Cada afirmacao aqui corresponde a um campo que
/// <c>frontend/src/app/model/health.ts</c> declara: se o corpo mudar de forma, a tela cai no
/// estado vazio sem erro nenhum, e so um teste deste nivel percebe.
/// </summary>
public sealed class HealthIndicatorsContractTests : IClassFixture<RunningPortal>
{
    private readonly RunningPortal _portal;

    public HealthIndicatorsContractTests(RunningPortal portal)
    {
        _portal = portal;
    }

    [Fact]
    public async Task TheBodyCarriesTheFieldsTheScreenDeclares()
    {
        JsonObject body = await GetBodyAsync("571289");

        Assert.Equal(
            ["diagnosis", "dissolvedGases", "equipmentId", "physicalChemical",
             "selectedAnalysis", "tag", "totalizerChecks", "unreadableResults"],
            body.Select(pair => pair.Key).Order());

        JsonObject gases = body["dissolvedGases"]!.AsObject();

        Assert.Equal(
            ["analyses", "analysisDates", "conformity", "gases", "report", "verdict"],
            gases.Select(pair => pair.Key).Order());

        JsonObject physicalChemical = body["physicalChemical"]!.AsObject();

        Assert.Equal(
            ["analyses", "columns", "conformity", "nominalVoltage", "report", "tests", "verdict"],
            physicalChemical.Select(pair => pair.Key).Order());
    }

    /// <summary>
    /// Com <c>DefaultIgnoreCondition</c> ignorando nulo, a chave desaparece do corpo e a
    /// tela nao distingue visao geral de analise ausente.
    /// </summary>
    [Fact]
    public async Task ANullFieldStaysPresentInTheBody()
    {
        JsonObject body = await GetBodyAsync("571289");

        Assert.True(body.ContainsKey("selectedAnalysis"));
        Assert.True(body.ContainsKey("diagnosis"));
        Assert.Null(body["selectedAnalysis"]);
        Assert.Null(body["diagnosis"]);
    }

    [Fact]
    public async Task FieldNamesComeOutInCamelCase()
    {
        JsonObject body = await GetBodyAsync("571289");
        JsonObject firstColumn = body["physicalChemical"]!["columns"]!.AsArray().Count > 0
            ? body["physicalChemical"]!["columns"]!.AsArray()[0]!.AsObject()
            : body["dissolvedGases"]!["gases"]!.AsArray()[0]!.AsObject();

        Assert.DoesNotContain(firstColumn.Select(pair => pair.Key), key => char.IsUpper(key[0]));
        Assert.False(body.ContainsKey("EquipmentId"));
    }

    [Fact]
    public async Task NotDetectedArrivesAsTextAndAbsenceAsNull()
    {
        JsonObject body = await GetBodyAsync("571289");
        JsonArray gases = body["dissolvedGases"]!["gases"]!.AsArray();

        JsonNode acetylene = gases.Single(gas => (string?)gas!["key"] == "c2h2")!;

        Assert.Equal("ND", (string?)acetylene["results"]!.AsArray()[0]);
    }

    /// <summary>
    /// A ultima coluna e a analise mais recente, e no equipamento 111.594/1 ela e o laudo
    /// real que veio com as seis grandezas em branco. A coluna e tomada pelo fim de
    /// proposito: as anteriores sao da serie escrita a mao e tem numero, e afirmar sobre a
    /// primeira coluna seria afirmar sobre dado inventado.
    /// </summary>
    [Fact]
    public async Task ATestNotPerformedArrivesAsNullAndNotAsZero()
    {
        JsonObject body = await GetBodyAsync("111.594/1");
        JsonArray tests = body["physicalChemical"]!["tests"]!.AsArray();

        Assert.NotEmpty(tests);
        Assert.All(tests, test =>
        {
            JsonArray values = test!["valuesByAnalysis"]!.AsArray();
            Assert.Null(values[^1]);
        });
    }

    [Fact]
    public async Task AnItemKeyIsNotConvertedToCamelCase()
    {
        JsonObject body = await GetBodyAsync("111.594/1");
        JsonObject values = body["physicalChemical"]!["analyses"]!.AsArray()[0]!["values"]!.AsObject();

        Assert.True(values.ContainsKey("fatorDePotencia"));
        Assert.True(values.ContainsKey("tensaoInterfacial"));
    }

    [Fact]
    public async Task ASerialNumberWithASlashSurvivesInTheRoute()
    {
        HttpResponseMessage response = await RequestAsync("111.594/1");

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
    }

    [Fact]
    public async Task EquipmentWithoutAnalysisAnswersNotFoundWithACode()
    {
        HttpResponseMessage response = await RequestAsync("numero-que-nao-existe");

        Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);

        JsonObject problem = JsonNode.Parse(await response.Content.ReadAsStringAsync())!.AsObject();
        Assert.Equal("equipment-without-analysis", (string?)problem["code"]);
    }

    /// <summary>
    /// O corpo de erro e o mesmo em toda a API porque so um lugar o monta. Aqui se mede o
    /// que de fato sai no fio: o <c>type</c> com endereco proprio por codigo, o
    /// <c>traceId</c> que liga a resposta a linha de log, e titulo e detalhe como frases
    /// distintas. Os tres eram ausentes enquanto as treze linhas estavam copiadas nos dois
    /// controllers, e o titulo era a mesma frase do detalhe.
    /// </summary>
    [Fact]
    public async Task TheProblemBodyNamesItsTypeAndCarriesTheTraceIdentifier()
    {
        HttpResponseMessage response = await RequestAsync("numero-que-nao-existe");

        JsonObject problem = JsonNode.Parse(await response.Content.ReadAsStringAsync())!.AsObject();

        Assert.Equal(
            "https://portal.lactec.org.br/problemas/equipment-without-analysis",
            (string?)problem["type"]);
        Assert.False(string.IsNullOrWhiteSpace((string?)problem["traceId"]));
        Assert.NotEqual((string?)problem["title"], (string?)problem["detail"]);
    }

    [Fact]
    public async Task AnUnreadableIdentifierAnswersBadRequest()
    {
        HttpResponseMessage response = await _portal.CreateClient()
            .GetAsync("/api/equipments/%20/health");

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
    }

    // Base 64 valido cujos bytes nao formam UTF-8. A leitura permissiva devolvia
    // texto com caractere de substituicao, e o endereco de lixo seguia como chave
    // de equipamento: a resposta afirmava que o equipamento existe e nao tem laudo.
    [Fact]
    public async Task AnIdentifierWithInvalidBytesIsRefusedAsUnreadable()
    {
        HttpResponseMessage response = await _portal.CreateClient()
            .GetAsync("/api/equipments/____/health");

        JsonObject problem = JsonNode.Parse(await response.Content.ReadAsStringAsync())!.AsObject();

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.Equal("invalid-identifier", (string?)problem["code"]);
    }

    // O protocolo pedido pela rota pode existir no inquilino e pertencer a outro
    // transformador. Sem a comparacao de chave, a tela nomearia a analise de um
    // equipamento enquanto desenha a tabela de outro.
    [Fact]
    public async Task AnAnalysisOfAnotherEquipmentDoesNotNameTheSelectedAnalysis()
    {
        string equipment = OpaqueIdentifier.Encode("571289");
        string strangerReport = OpaqueIdentifier.Encode("01439/2026-004");

        HttpResponseMessage response = await _portal.CreateClient()
            .GetAsync($"/api/equipments/{equipment}/health/reports/{strangerReport}");

        JsonObject body = JsonNode.Parse(await response.Content.ReadAsStringAsync())!.AsObject();

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.Equal("571289", (string?)body["tag"]);
        Assert.Null(body["selectedAnalysis"]?.GetValue<object?>());
    }

    [Fact]
    public async Task TheSelectedAnalysisArrivesNamedWhenTheRouteAsksForOne()
    {
        string equipment = OpaqueIdentifier.Encode("571289");
        string analysis = OpaqueIdentifier.Encode("01417/2026-001");

        HttpResponseMessage response = await _portal.CreateClient()
            .GetAsync($"/api/equipments/{equipment}/health/reports/{analysis}");

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);

        JsonObject body = JsonNode.Parse(await response.Content.ReadAsStringAsync())!.AsObject();
        JsonObject selected = body["selectedAnalysis"]!.AsObject();

        Assert.Equal(analysis, (string?)selected["id"]);
        Assert.Equal("2026-06-23", (string?)selected["date"]);
        Assert.Equal("LACTEC-02272/2026", (string?)selected["label"]);

        // O identificador da analise selecionada tem que ser o mesmo que o historico de
        // laudos publica para aquele laudo. Voltava o protocolo cru, com barra, enquanto o
        // historico ja publicava a forma opaca: a tela nao conseguia casar os dois lados, e
        // montar um endereco com o protocolo cru quebrava a rota.
        HttpResponseMessage historyResponse = await _portal.CreateClient()
            .GetAsync($"/api/equipments/{equipment}/reports");

        JsonArray history = JsonNode.Parse(await historyResponse.Content.ReadAsStringAsync())!.AsArray();

        Assert.Contains(
            history,
            report => (string?)report!["id"] == (string?)selected["id"]);
    }

    [Fact]
    public async Task EveryPublishedTotalizerArrivesCheckedInTheBody()
    {
        JsonObject body = await GetBodyAsync("571289");
        JsonArray checks = body["totalizerChecks"]!.AsArray();
        int gasAnalyses = body["dissolvedGases"]!["analyses"]!.AsArray().Count;

        Assert.Equal(gasAnalyses * 2, checks.Count);
        Assert.All(checks, check => Assert.Equal("matches", (string?)check!["result"]));
    }

    /// <summary>
    /// O veredito viaja como texto em caixa baixa com hifen. Sem este teste, tirar o
    /// conversor de enum do <c>Program</c> trocava "confere" pelo numero zero e a tela
    /// deixava de reconhecer o caso, sem erro nenhum na suite.
    /// </summary>
    [Fact]
    public async Task TheTotalizerVerdictTravelsAsLowercaseHyphenatedText()
    {
        JsonObject body = await GetBodyAsync("571289");
        JsonArray checks = body["totalizerChecks"]!.AsArray();

        Assert.All(checks, check =>
        {
            JsonNode result = check!["result"]!;

            Assert.Equal(JsonValueKind.String, result.GetValueKind());
            Assert.Matches("^[a-z]+(-[a-z]+)*$", (string)result!);
        });
    }

    /// <summary>
    /// O laudo com os tres gases de Duval nao detectados fecha os dois totalizadores, porque
    /// nao detectado vale zero na soma. E o caso real de total de combustiveis igual a doze
    /// com metano, etileno e acetileno todos em ND.
    /// </summary>
    [Fact]
    public async Task AReportWithDuvalGasesNotDetectedStillChecksTheTotalizers()
    {
        JsonObject body = await GetBodyAsync("884120");

        JsonNode combustibles = body["totalizerChecks"]!.AsArray()
            .Single(check => (string?)check!["key"] == "combustiveis")!;

        Assert.Equal("matches", (string?)combustibles["result"]);
        Assert.Equal(12, (decimal?)combustibles["measuredSum"]);
        Assert.Equal(0, (int?)combustibles["gasesWithoutTest"]);
    }

    private async Task<JsonObject> GetBodyAsync(string equipmentKey)
    {
        HttpResponseMessage response = await RequestAsync(equipmentKey);
        response.EnsureSuccessStatusCode();

        return JsonNode.Parse(await response.Content.ReadAsStringAsync())!.AsObject();
    }

    private Task<HttpResponseMessage> RequestAsync(string equipmentKey)
    {
        string identifier = OpaqueIdentifier.Encode(equipmentKey);

        return _portal.CreateClient().GetAsync($"/api/equipments/{identifier}/health");
    }
}
