using System.Net;
using System.Net.Http.Json;
using System.Text.Json.Nodes;

using Portal.Adapters.BackendLab;
using Portal.Application.Authentication;

using Xunit;

namespace Portal.Tests.Authentication;

public sealed class ClientLoginContractTests
{
    private const string LoginResponse = """
        {
          "accessToken": "spring-access-token",
          "tokenType": "Bearer",
          "expiresIn": 3600,
          "user": {
            "id": "cliente-42",
            "name": "Marina Costa",
            "email": "marina@empresa.com",
            "role": "Client",
            "companies": [
              { "id": "empresa-9", "name": "Empresa Exemplo" }
            ]
          }
        }
        """;

    private const string ResponseWithoutAccessToken = """
        {
          "tokenType": "Bearer",
          "expiresIn": 3600,
          "user": {
            "id": "1",
            "name": "Nome",
            "email": "a@b.com",
            "role": "Client",
            "companies": []
          }
        }
        """;

    private const string ResponseWithDifferentProfile = """
        {
          "accessToken": "token",
          "tokenType": "Bearer",
          "expiresIn": 3600,
          "user": {
            "id": "1",
            "name": "Nome",
            "email": "a@b.com",
            "role": "Administrator",
            "companies": []
          }
        }
        """;

    [Fact]
    public async Task LoginValidoPreservaOTokenEDevolveOsDadosDoCliente()
    {
        ScriptedBackendLabHandler backend = ScriptedBackendLabHandler.Returning(HttpStatusCode.OK, LoginResponse);
        using AuthenticationPortal portal = new(backend);
        using HttpClient client = portal.CreateClient();

        using HttpResponseMessage response = await client.PostAsJsonAsync("/auth/login", new
        {
            email = "marina@empresa.com",
            password = "senha123"
        });

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.NotNull(response.Headers.CacheControl);
        Assert.True(response.Headers.CacheControl!.NoStore);
        JsonObject body = await ReadBodyAsync(response);
        Assert.Equal(
            ["Token", "expiresIn", "tokenType", "user"],
            body.Select(property => property.Key).Order(StringComparer.Ordinal));
        Assert.Equal("spring-access-token", (string?)body["Token"]);
        Assert.Equal("Bearer", (string?)body["tokenType"]);
        Assert.Equal(3600, (long?)body["expiresIn"]);

        JsonObject user = body["user"]!.AsObject();
        Assert.Equal("cliente-42", (string?)user["id"]);
        Assert.Equal("Marina Costa", (string?)user["name"]);
        Assert.Equal("marina@empresa.com", (string?)user["email"]);
        Assert.Equal("Client", (string?)user["role"]);

        JsonObject company = user["companies"]!.AsArray().Single()!.AsObject();
        Assert.Equal("empresa-9", (string?)company["id"]);
        Assert.Equal("Empresa Exemplo", (string?)company["name"]);

        Assert.Equal(HttpMethod.Post, backend.RequestMethod);
        Assert.Equal("/api/auth/login", backend.RequestPathAndQuery);
        Assert.Equal(1, backend.RequestCount);
    }

    [Fact]
    public async Task LoginValidoEncaminhaASenhaSemRemoverEspacos()
    {
        const string password = "  senha com espacos  ";
        ScriptedBackendLabHandler backend = ScriptedBackendLabHandler.Returning(HttpStatusCode.OK, LoginResponse);
        using AuthenticationPortal portal = new(backend);
        using HttpClient client = portal.CreateClient();

        using HttpResponseMessage response = await client.PostAsJsonAsync("/auth/login", new
        {
            email = "marina@empresa.com",
            password
        });

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        JsonObject forwarded = JsonNode.Parse(backend.RequestBody!)!.AsObject();
        Assert.Equal(
            ["email", "password"],
            forwarded.Select(property => property.Key).Order(StringComparer.Ordinal));
        Assert.Equal(password, (string?)forwarded["password"]);
        Assert.Equal("marina@empresa.com", (string?)forwarded["email"]);
    }

    [Fact]
    public async Task LoginValidoAceitaClienteSemEmpresasVinculadas()
    {
        const string responseBody = """
            {
              "accessToken": "token-sem-empresa",
              "tokenType": "Bearer",
              "expiresIn": 300,
              "user": {
                "id": "cliente-sem-empresa",
                "name": "Cliente Sem Empresa",
                "email": "cliente@empresa.com",
                "role": "Client",
                "companies": []
              }
            }
            """;
        ScriptedBackendLabHandler backend = ScriptedBackendLabHandler.Returning(HttpStatusCode.OK, responseBody);
        using AuthenticationPortal portal = new(backend);
        using HttpClient client = portal.CreateClient();

        using HttpResponseMessage response = await client.PostAsJsonAsync("/auth/login", new
        {
            email = "cliente@empresa.com",
            password = "senha123"
        });

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        JsonObject body = await ReadBodyAsync(response);
        Assert.Empty(body["user"]!["companies"]!.AsArray());
    }

    [Fact]
    public async Task CredenciaisRecusadasPeloSpringRetornam401ComCodigoConhecido()
    {
        ScriptedBackendLabHandler backend = ScriptedBackendLabHandler.Returning(
            HttpStatusCode.Unauthorized,
            "{}");
        using AuthenticationPortal portal = new(backend);
        using HttpClient client = portal.CreateClient();

        using HttpResponseMessage response = await client.PostAsJsonAsync("/auth/login", new
        {
            email = "marina@empresa.com",
            password = "senha-incorreta"
        });

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
        JsonObject problem = await ReadBodyAsync(response);
        Assert.Equal("invalid-credentials", (string?)problem["code"]);
        Assert.Equal(1, backend.RequestCount);
    }

    [Fact]
    public async Task RequisicaoRecusadaPeloSpringRetorna400ComCodigoConhecido()
    {
        ScriptedBackendLabHandler backend = ScriptedBackendLabHandler.Returning(
            HttpStatusCode.BadRequest,
            "{}");
        using AuthenticationPortal portal = new(backend);
        using HttpClient client = portal.CreateClient();

        using HttpResponseMessage response = await client.PostAsJsonAsync("/auth/login", new
        {
            email = "marina@empresa.com",
            password = "senha-invalida-para-a-fonte"
        });

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        JsonObject problem = await ReadBodyAsync(response);
        Assert.Equal("invalid-login-request", (string?)problem["code"]);
        Assert.Equal(1, backend.RequestCount);
    }

    [Theory]
    [InlineData(500, "application/json", "{}")]
    [InlineData(200, "text/html", "<html>indisponivel</html>")]
    [InlineData(200, "application/json", "{")]
    [InlineData(200, "application/json", ResponseWithoutAccessToken)]
    [InlineData(200, "application/json", ResponseWithDifferentProfile)]
    public async Task RespostaInvalidaOuFalhaDoSpringRetorna502ComCodigoConhecido(
        int statusCode,
        string mediaType,
        string body)
    {
        ScriptedBackendLabHandler backend = ScriptedBackendLabHandler.Returning(
            (HttpStatusCode)statusCode,
            body,
            mediaType);
        using AuthenticationPortal portal = new(backend);
        using HttpClient client = portal.CreateClient();

        using HttpResponseMessage response = await client.PostAsJsonAsync("/auth/login", new
        {
            email = "marina@empresa.com",
            password = "senha123"
        });

        Assert.Equal(HttpStatusCode.BadGateway, response.StatusCode);
        JsonObject problem = await ReadBodyAsync(response);
        Assert.Equal("authentication-source-unavailable", (string?)problem["code"]);
        Assert.Equal(1, backend.RequestCount);
    }

    [Theory]
    [InlineData("", "senha123")]
    [InlineData("marina@empresa.com", "")]
    [InlineData("nao-e-um-email", "senha123")]
    public async Task DadosInvalidosRetornam400SemConsultarOSpring(string email, string password)
    {
        ScriptedBackendLabHandler backend = ScriptedBackendLabHandler.Returning(HttpStatusCode.OK, LoginResponse);
        using AuthenticationPortal portal = new(backend);
        using HttpClient client = portal.CreateClient();

        using HttpResponseMessage response = await client.PostAsJsonAsync("/auth/login", new { email, password });

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.Equal(0, backend.RequestCount);
    }

    [Fact]
    public async Task FalhaDeConexaoComOSpringRetorna502()
    {
        ScriptedBackendLabHandler backend = ScriptedBackendLabHandler.Failing(
            new HttpRequestException("conexao indisponivel"));
        using AuthenticationPortal portal = new(backend);
        using HttpClient client = portal.CreateClient();

        using HttpResponseMessage response = await client.PostAsJsonAsync("/auth/login", new
        {
            email = "marina@empresa.com",
            password = "senha123"
        });

        Assert.Equal(HttpStatusCode.BadGateway, response.StatusCode);
        JsonObject problem = await ReadBodyAsync(response);
        Assert.Equal("authentication-source-unavailable", (string?)problem["code"]);
    }

    [Fact]
    public async Task TempoEsgotadoNaConsultaAoSpringRetorna502()
    {
        ScriptedBackendLabHandler backend = ScriptedBackendLabHandler.Failing(
            new TaskCanceledException("tempo esgotado"));
        using AuthenticationPortal portal = new(backend);
        using HttpClient client = portal.CreateClient();

        using HttpResponseMessage response = await client.PostAsJsonAsync("/auth/login", new
        {
            email = "marina@empresa.com",
            password = "senha123"
        });

        Assert.Equal(HttpStatusCode.BadGateway, response.StatusCode);
        JsonObject problem = await ReadBodyAsync(response);
        Assert.Equal("authentication-source-unavailable", (string?)problem["code"]);
    }

    [Fact]
    public async Task CancelamentoDoClienteInterrompeAConsultaAoSpring()
    {
        ScriptedBackendLabHandler backend = ScriptedBackendLabHandler.WaitingForCancellation();
        using HttpClient client = new(backend)
        {
            BaseAddress = new Uri("https://backend-lab.test/")
        };
        BackendLabAdapter adapter = new(client);
        using CancellationTokenSource cancellation = new();

        Task<ClientSession?> request = adapter.AuthenticateAsync(
            "marina@empresa.com", "senha123", cancellation.Token);

        await backend.RequestStarted.Task.WaitAsync(TimeSpan.FromSeconds(5));
        cancellation.Cancel();

        await Assert.ThrowsAnyAsync<OperationCanceledException>(() => request);
    }

    private static async Task<JsonObject> ReadBodyAsync(HttpResponseMessage response)
    {
        string json = await response.Content.ReadAsStringAsync();
        return JsonNode.Parse(json)!.AsObject();
    }
}
