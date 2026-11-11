using System.IdentityModel.Tokens.Jwt;
using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text.Json.Nodes;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.AspNetCore.TestHost;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Options;
using Microsoft.IdentityModel.Tokens;
using Portal.Adapters.BackendAuth;
using Portal.Adapters.Quotes;
using Portal.Application.Quotes;
using Portal.Application.Authentication;
using Portal.Tests.AutoLab;
using Xunit;

namespace Portal.Tests.Authentication;

public sealed class LactecPortalContractTests
{
    private const string Identity = """
        {"id":"42","name":"Marina Costa","email":"marina@empresa.com","role":"CLIENTE",
        "serviceCode":"portal-web","authorizations":{"companyCnpjs":["04252011000110","12ABC34501DE35"]}}
        """;

    [Theory]
    [InlineData(false)]
    [InlineData(true)]
    public async Task LoginEmiteJwtDoPortalEMePreservaIdentidadeSemOutraConsulta(bool withoutCompanies)
    {
        string body = withoutCompanies ? Identity.Replace("\"04252011000110\",\"12ABC34501DE35\"", "") : Identity;
        using var backend = new AuthHandler(HttpStatusCode.OK, body);
        using var basePortal = new AuthPortal(backend);
        using var portal = basePortal.WithWebHostBuilder(builder => builder.ConfigureTestServices(services =>
            services.AddScoped<IQuoteRepository, FakeSalesforceQuoteRepository>()));
        using HttpClient client = portal.CreateClient();
        using var login = await Login(client, "  Marina@Empresa.com  ", "  Senha original  ");
        Assert.Equal(HttpStatusCode.OK, login.StatusCode);
        Assert.True(login.Headers.CacheControl!.NoStore);
        JsonObject response = await Body(login);
        Assert.Equal(["Token", "expiresIn", "tokenType", "user"], response.Select(pair => pair.Key).Order(StringComparer.Ordinal));
        Assert.Equal(900, (int?)response["expiresIn"]);
        Assert.Equal("Bearer", (string?)response["tokenType"]);
        JsonObject user = response["user"]!.AsObject();
        Assert.Null(user["companies"]);
        Assert.Null(user["serviceId"]);
        Assert.Equal("portal-web", (string?)user["serviceCode"]);
        Assert.Equal("CLIENTE", (string?)user["role"]);
        string token = (string)response["Token"]!;
        JwtSecurityToken jwt = new JwtSecurityTokenHandler().ReadJwtToken(token);
        Assert.Equal(SecurityAlgorithms.HmacSha256, jwt.Header.Alg);
        Assert.Equal("portal-test", jwt.Issuer);
        Assert.Equal("portal-web-test", Assert.Single(jwt.Audiences));
        Assert.Equal("42", jwt.Subject);
        Assert.Equal("lactec", jwt.Payload["authSource"]);
        Assert.Equal(900, (jwt.ValidTo - jwt.IssuedAt).TotalSeconds);
        Assert.NotNull(jwt.Payload["jti"]);
        Assert.Equal(withoutCompanies ? 0 : 2, JsonNode.Parse(jwt.Payload.SerializeToJson())!["companyCnpjs"]!.AsArray().Count);
        Assert.Equal(AuthPortal.ServiceToken, backend.Authorization);
        Assert.Equal("/api/auth/login", backend.Path);
        JsonObject forwarded = JsonNode.Parse(backend.RequestBody!)!.AsObject();
        Assert.Equal("marina@empresa.com", (string?)forwarded["email"]);
        Assert.Equal("  Senha original  ", (string?)forwarded["password"]);
        Assert.DoesNotContain(AuthPortal.ServiceToken, await login.Content.ReadAsStringAsync());

        client.DefaultRequestHeaders.Authorization = new("Bearer", token);
        using var me = await client.GetAsync("/auth/me");
        Assert.Equal(HttpStatusCode.OK, me.StatusCode);
        Assert.True(me.Headers.CacheControl!.NoStore);
        Assert.True(JsonNode.DeepEquals(user, await Body(me)));
        foreach (string path in new[] { "/api/quotes", "/api/equipments" })
        {
            using var protectedResponse = await client.GetAsync(path);
            Assert.Equal(HttpStatusCode.OK, protectedResponse.StatusCode);
        }
        Assert.Equal(1, backend.Count);
    }

    [Theory]
    [InlineData("/auth/me")]
    [InlineData("/api/quotes")]
    [InlineData("/api/equipments")]
    [InlineData("/api/lab-results")]
    public async Task RotasProtegidasExigemBearerSemConsultarBackendAuth(string path)
    {
        using var backend = new AuthHandler(HttpStatusCode.OK, Identity);
        using var portal = new AuthPortal(backend);
        using HttpClient client = portal.CreateClient();
        using var response = await client.GetAsync(path);
        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
        Assert.Equal("Bearer", Assert.Single(response.Headers.WwwAuthenticate).Scheme);
        Assert.True(response.Headers.CacheControl!.NoStore);
        Assert.Equal("invalid-portal-token", (string?)(await Body(response))["code"]);
        Assert.Equal(0, backend.Count);
    }

    [Theory]
    [InlineData("expired")]
    [InlineData("future")]
    [InlineData("issuer")]
    [InlineData("audience")]
    [InlineData("signature")]
    [InlineData("algorithm")]
    [InlineData("serviceCode")]
    [InlineData("authSource")]
    [InlineData("sub")]
    [InlineData("nome")]
    [InlineData("email")]
    [InlineData("jti")]
    [InlineData("malformed")]
    [InlineData("service-token")]
    public async Task JwtInvalidoNaoAbreSessao(string defect)
    {
        using var backend = new AuthHandler(HttpStatusCode.OK, Identity);
        using var portal = new AuthPortal(backend);
        using HttpClient client = portal.CreateClient();
        string token = defect switch
        {
            "malformed" => "invalid.token",
            "service-token" => AuthPortal.ServiceToken,
            _ => SignedToken(defect)
        };
        client.DefaultRequestHeaders.Authorization = new("Bearer", token);
        using var response = await client.GetAsync("/auth/me");
        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
        Assert.Equal(0, backend.Count);
    }

    [Fact]
    public async Task JwtValidoComOutroPapelRetorna403()
    {
        using var backend = new AuthHandler(HttpStatusCode.OK, Identity);
        using var portal = new AuthPortal(backend);
        using HttpClient client = portal.CreateClient();
        client.DefaultRequestHeaders.Authorization = new("Bearer", SignedToken("roles"));
        using var response = await client.GetAsync("/api/quotes");
        Assert.Equal(HttpStatusCode.Forbidden, response.StatusCode);
        Assert.Equal(0, backend.Count);
    }

    [Theory]
    [InlineData(401, false, null, 401, "invalid-credentials")]
    [InlineData(401, true, null, 502, "authentication-source-unavailable")]
    [InlineData(403, false, null, 502, "authentication-source-unavailable")]
    [InlineData(400, false, null, 400, "invalid-login-request")]
    [InlineData(429, false, "900", 429, "login-cooldown")]
    [InlineData(429, false, null, 502, "authentication-source-unavailable")]
    [InlineData(500, false, null, 502, "authentication-source-unavailable")]
    [InlineData(302, false, null, 502, "authentication-source-unavailable")]
    public async Task FalhasDaOrigemTemStatusDistintosSemVazarDetalhes(int status, bool challenge,
        string? retryAfter, int expected, string code)
    {
        using var backend = new AuthHandler((HttpStatusCode)status, "secret-upstream-body", challenge, retryAfter);
        using var portal = new AuthPortal(backend);
        using HttpClient client = portal.CreateClient();
        using var response = await Login(client);
        Assert.Equal(expected, (int)response.StatusCode);
        Assert.True(response.Headers.CacheControl!.NoStore);
        Assert.Equal(code, (string?)(await Body(response))["code"]);
        Assert.DoesNotContain("secret-upstream-body", await response.Content.ReadAsStringAsync());
        Assert.Equal(retryAfter is not null && expected == 429 ? 900 : null, response.Headers.RetryAfter?.Delta?.TotalSeconds);
        Assert.Equal(1, backend.Count);
    }

    [Theory]
    [InlineData("{")]
    [InlineData("null")]
    [InlineData("{}")]
    [InlineData("other-service")]
    [InlineData("other-role")]
    [InlineData("no-authorizations")]
    [InlineData("invalid-company")]
    public async Task IdentidadeIncompletaOuDeOutroServicoNaoEmiteToken(string defect)
    {
        string body = defect switch
        {
            "other-service" => Identity.Replace("portal-web", "other-service"),
            "other-role" => Identity.Replace("CLIENTE", "ADMIN"),
            "no-authorizations" => Identity.Replace("authorizations", "ignored"),
            "invalid-company" => Identity.Replace("04252011000110", "invalid"),
            _ => defect
        };
        using var backend = new AuthHandler(HttpStatusCode.OK, body);
        using var portal = new AuthPortal(backend);
        using HttpClient client = portal.CreateClient();
        using var response = await Login(client);
        Assert.Equal(HttpStatusCode.BadGateway, response.StatusCode);
        Assert.Null((await Body(response))["Token"]);
    }

    [Theory]
    [InlineData(false)]
    [InlineData(true)]
    public async Task ConexaoOuTimeoutRetorna502(bool timeout)
    {
        using var backend = new AuthHandler(HttpStatusCode.OK, Identity)
        { Failure = timeout ? new TaskCanceledException() : new HttpRequestException() };
        using var portal = new AuthPortal(backend);
        using HttpClient client = portal.CreateClient();
        using var response = await Login(client);
        Assert.Equal(HttpStatusCode.BadGateway, response.StatusCode);
    }

    [Fact]
    public async Task PreflightEOpenApiSaoPublicosSemConsultarBackendAuth()
    {
        using var backend = new AuthHandler(HttpStatusCode.OK, Identity);
        using var portal = new AuthPortal(backend);
        using HttpClient client = portal.CreateClient();
        using var preflight = new HttpRequestMessage(HttpMethod.Options, "/api/quotes");
        preflight.Headers.Add("Origin", "http://localhost:4200");
        preflight.Headers.Add("Access-Control-Request-Method", "GET");
        preflight.Headers.Add("Access-Control-Request-Headers", "authorization");
        using var response = await client.SendAsync(preflight);
        Assert.Equal(HttpStatusCode.NoContent, response.StatusCode);
        using var document = await client.GetAsync("/openapi/v1.json");
        Assert.Equal(HttpStatusCode.OK, document.StatusCode);
        JsonObject json = await Body(document);
        Assert.Equal("bearer", (string?)json["components"]!["securitySchemes"]!["Bearer"]!["scheme"]);
        Assert.Null(json["paths"]!["/auth/login"]!["post"]!["security"]);
        Assert.NotNull(json["paths"]!["/auth/me"]!["get"]!["security"]);
        Assert.Equal(0, backend.Count);
    }

    [Fact]
    public async Task DadosInvalidosNaoConsultamOrigemENaoPermitemCache()
    {
        using var backend = new AuthHandler(HttpStatusCode.OK, Identity);
        using var portal = new AuthPortal(backend);
        using HttpClient client = portal.CreateClient();
        using var response = await Login(client, "invalid", "");
        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.True(response.Headers.CacheControl!.NoStore);
        Assert.Equal(0, backend.Count);
    }

    [Theory]
    [InlineData("BackendAuth:BaseUrl", "")]
    [InlineData("BackendAuth:ServiceToken", "")]
    [InlineData("BackendAuth:ServiceToken", "admin-token")]
    [InlineData("BackendAuth:ServiceCode", "")]
    [InlineData("PortalJwt:Issuer", "")]
    [InlineData("PortalJwt:Audience", "")]
    [InlineData("PortalJwt:SigningKey", "")]
    [InlineData("PortalJwt:SigningKey", "not-base64")]
    [InlineData("PortalJwt:SigningKey", "YQ==")]
    [InlineData("PortalJwt:ExpirationSeconds", "0")]
    [InlineData("Salesforce:Url", "")]
    [InlineData("Salesforce:Url", "/services/oauth2/token")]
    [InlineData("Salesforce:Url", "ftp://salesforce.test/")]
    [InlineData("Salesforce:GrantType", "")]
    [InlineData("Salesforce:ClientId", "")]
    [InlineData("Salesforce:ClientSecret", " ")]
    public void ConfiguracaoIncompletaImpedeInicializacao(string key, string value)
    {
        using var backend = new AuthHandler(HttpStatusCode.OK, Identity);
        using var portal = new AuthPortal(backend, key, value);
        Assert.Throws<OptionsValidationException>(() => portal.CreateClient());
    }

    [Fact]
    public async Task RenovacaoTrocaOTokenDoLoginPorOutroDaMesmaSessaoSemConsultarBackendAuth()
    {
        using var backend = new AuthHandler(HttpStatusCode.OK, Identity);
        using var portal = new AuthPortal(backend);
        using HttpClient client = portal.CreateClient();
        using var login = await Login(client);
        string first = (string)(await Body(login))["Token"]!;

        using var renewed = await Refresh(client, first);

        Assert.Equal(HttpStatusCode.OK, renewed.StatusCode);
        Assert.True(renewed.Headers.CacheControl!.NoStore);
        JsonObject response = await Body(renewed);
        Assert.Equal(900, (int?)response["expiresIn"]);
        Assert.Equal("CLIENTE", (string?)response["user"]!["role"]);
        JwtSecurityToken before = new JwtSecurityTokenHandler().ReadJwtToken(first);
        JwtSecurityToken after = new JwtSecurityTokenHandler().ReadJwtToken((string)response["Token"]!);
        Assert.Equal(before.Payload[PortalSessionClaim], after.Payload[PortalSessionClaim]);
        Assert.NotEqual(before.Id, after.Id);
        Assert.Equal(2, JsonNode.Parse(after.Payload.SerializeToJson())!["companyCnpjs"]!.AsArray().Count);
        Assert.Equal(1, backend.Count);

        client.DefaultRequestHeaders.Authorization = new("Bearer", (string)response["Token"]!);
        using var me = await client.GetAsync("/auth/me");
        Assert.Equal(HttpStatusCode.OK, me.StatusCode);
    }

    [Fact]
    public async Task UmTokenVencidoDentroDaJanelaDeRenovacaoAindaRenova()
    {
        using var portal = new AuthPortal(new AuthHandler(HttpStatusCode.OK, Identity));
        using HttpClient client = portal.CreateClient();
        DateTime now = DateTime.UtcNow;

        using var renewed = await Refresh(client, SessionToken(expires: now.AddMinutes(-20), sessionStart: now.AddHours(-1)));

        Assert.Equal(HttpStatusCode.OK, renewed.StatusCode);
    }

    [Fact]
    public async Task ARenovacaoNaoPassaDoLimiteDeDozeHorasDesdeOLogin()
    {
        using var portal = new AuthPortal(new AuthHandler(HttpStatusCode.OK, Identity));
        using HttpClient client = portal.CreateClient();
        DateTime now = DateTime.UtcNow;

        using var nearTheEnd = await Refresh(client, SessionToken(now.AddMinutes(5), now.AddHours(-12).AddMinutes(2)));
        Assert.Equal(HttpStatusCode.OK, nearTheEnd.StatusCode);
        Assert.InRange((int)(await Body(nearTheEnd))["expiresIn"]!, 1, 120);

        using var over = await Refresh(client, SessionToken(now.AddMinutes(5), now.AddHours(-12).AddSeconds(-1)));
        Assert.Equal(HttpStatusCode.Unauthorized, over.StatusCode);
        Assert.Equal("session-expired", (string?)(await Body(over))["code"]);
    }

    [Theory]
    [InlineData("expirado-demais")]
    [InlineData("sem-inicio-de-sessao")]
    [InlineData("inicio-no-futuro")]
    [InlineData("assinatura")]
    [InlineData("outro-servico")]
    [InlineData("sem-cabecalho")]
    [InlineData("token-de-servico")]
    public async Task RenovacaoRecusaTokenQueNaoPodeSerRenovado(string defect)
    {
        using var backend = new AuthHandler(HttpStatusCode.OK, Identity);
        using var portal = new AuthPortal(backend);
        using HttpClient client = portal.CreateClient();
        DateTime now = DateTime.UtcNow;
        string? token = defect switch
        {
            "expirado-demais" => SessionToken(now.AddMinutes(-61), now.AddHours(-2)),
            "sem-inicio-de-sessao" => SessionToken(now.AddMinutes(5), null),
            "inicio-no-futuro" => SessionToken(now.AddMinutes(5), now.AddMinutes(1)),
            "assinatura" => SessionToken(now.AddMinutes(5), now.AddHours(-1), key: new byte[64]),
            "outro-servico" => SessionToken(now.AddMinutes(5), now.AddHours(-1), serviceCode: "outro"),
            "token-de-servico" => AuthPortal.ServiceToken,
            _ => null
        };

        using var response = await Refresh(client, token);

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
        AuthenticationHeaderValue challenge = Assert.Single(response.Headers.WwwAuthenticate);
        Assert.Equal("Bearer", challenge.Scheme);
        Assert.Equal(token is null ? null : "error=\"invalid_token\"", challenge.Parameter);
        Assert.True(response.Headers.CacheControl!.NoStore);
        Assert.Equal("session-expired", (string?)(await Body(response))["code"]);
        Assert.Equal(0, backend.Count);
    }

    private const string PortalSessionClaim = "auth_time";

    private static async Task<HttpResponseMessage> Refresh(HttpClient client, string? token)
    {
        using HttpRequestMessage request = new(HttpMethod.Post, "/auth/refresh");
        if (token is not null) request.Headers.Authorization = new("Bearer", token);
        return await client.SendAsync(request);
    }

    private static string SessionToken(DateTime expires, DateTime? sessionStart, byte[]? key = null, string serviceCode = "portal-web")
    {
        Dictionary<string, object> claims = new()
        {
            ["sub"] = "42", ["nome"] = "Marina Costa", ["email"] = "marina@empresa.com",
            ["roles"] = "CLIENTE", ["jti"] = Guid.NewGuid().ToString(), ["authSource"] = "lactec",
            ["serviceCode"] = serviceCode, ["companyCnpjs"] = new[] { "04252011000110" }
        };
        if (sessionStart is not null)
            claims[PortalSessionClaim] = new DateTimeOffset(sessionStart.Value).ToUnixTimeSeconds();
        DateTime issued = expires.AddMinutes(-15);
        JwtSecurityTokenHandler handler = new();
        return handler.WriteToken(handler.CreateToken(new SecurityTokenDescriptor
        {
            Claims = claims, Issuer = "portal-test", Audience = "portal-web-test",
            IssuedAt = issued, NotBefore = issued, Expires = expires,
            SigningCredentials = new SigningCredentials(
                new SymmetricSecurityKey(key ?? Convert.FromBase64String(AuthPortal.SigningKey)), SecurityAlgorithms.HmacSha256)
        }));
    }

    private static Task<HttpResponseMessage> Login(HttpClient client, string email = "marina@empresa.com", string password = "original") =>
        client.PostAsJsonAsync("/auth/login", new { email, password });
    private static async Task<JsonObject> Body(HttpResponseMessage response) =>
        JsonNode.Parse(await response.Content.ReadAsStringAsync())!.AsObject();

    private static string SignedToken(string defect)
    {
        Dictionary<string, object> claims = new()
        {
            ["sub"] = "42", ["nome"] = "Marina Costa", ["email"] = "marina@empresa.com",
            ["roles"] = "CLIENTE", ["jti"] = Guid.NewGuid().ToString(), ["authSource"] = "lactec",
            ["serviceCode"] = "portal-web", ["companyCnpjs"] = Array.Empty<string>()
        };
        if (claims.ContainsKey(defect)) claims[defect] = defect is "roles" ? "ADMIN" : "";
        DateTime now = DateTime.UtcNow;
        JwtSecurityTokenHandler handler = new();
        return handler.WriteToken(handler.CreateToken(new SecurityTokenDescriptor
        {
            Claims = claims, Issuer = defect == "issuer" ? "wrong" : "portal-test",
            Audience = defect == "audience" ? "wrong" : "portal-web-test",
            IssuedAt = now.AddHours(-1), NotBefore = defect == "future" ? now.AddMinutes(1) : now.AddHours(-1),
            Expires = defect == "expired" ? now.AddSeconds(-1) : now.AddMinutes(15),
            SigningCredentials = new SigningCredentials(new SymmetricSecurityKey(defect == "signature"
                ? new byte[64] : Convert.FromBase64String(AuthPortal.SigningKey)),
                defect == "algorithm" ? SecurityAlgorithms.HmacSha384 : SecurityAlgorithms.HmacSha256)
        }));
    }
}

internal sealed class AuthPortal(HttpMessageHandler handler, string? overrideKey = null, string? overrideValue = null)
    : WebApplicationFactory<Program>
{
    public static readonly string SigningKey = Convert.ToBase64String(Enumerable.Repeat((byte)42, 64).ToArray());
    public static readonly string ServiceToken = "svc_" + new string('a', 43);

    private readonly AutoLabTestDatabase _database = AutoLabTestDatabase.WithSample();

    protected override void Dispose(bool disposing)
    {
        base.Dispose(disposing);

        if (disposing) _database.Dispose();
    }

    protected override void ConfigureWebHost(IWebHostBuilder builder)
    {
        builder.UseSampleAutoLab(_database);
        builder.UseSetting("Portal:DataSource", "lactec");
        builder.UseSetting("BackendLab:BaseUrl", "");
        builder.UseSetting("BackendAuth:BaseUrl", "https://backend-auth.test/");
        builder.UseSetting("BackendAuth:ServiceToken", ServiceToken);
        builder.UseSetting("BackendAuth:ServiceCode", "portal-web");
        builder.UseSetting("PortalJwt:Issuer", "portal-test");
        builder.UseSetting("PortalJwt:Audience", "portal-web-test");
        builder.UseSetting("PortalJwt:SigningKey", SigningKey);
        // Sem dados de demonstracao, lactec exige o Salesforce na inicializacao. Os testes que
        // consultam o Salesforce trocam o HTTP; estes valores so passam pela validacao.
        builder.UseSetting("Salesforce:Url", "https://salesforce.test/");
        builder.UseSetting("Salesforce:GrantType", "client_credentials");
        builder.UseSetting("Salesforce:ClientId", "client-id-test");
        builder.UseSetting("Salesforce:ClientSecret", "client-secret-test");
        if (overrideKey is not null) builder.UseSetting(overrideKey, overrideValue);
        builder.ConfigureTestServices(services => services
            .AddHttpClient<IClientAuthenticator, BackendAuthAdapter>().ConfigurePrimaryHttpMessageHandler(() => handler));
    }
}

internal sealed class AuthHandler(HttpStatusCode status, string body, bool challenge = false, string? retryAfter = null)
    : HttpMessageHandler
{
    public int Count { get; private set; }
    public string? Authorization { get; private set; }
    public string? Path { get; private set; }
    public string? RequestBody { get; private set; }
    public Exception? Failure { get; init; }

    protected override async Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
    {
        Count++;
        Authorization = request.Headers.Authorization?.Parameter;
        Path = request.RequestUri?.AbsolutePath;
        RequestBody = await request.Content!.ReadAsStringAsync(cancellationToken);
        if (Failure is not null) throw Failure;
        var response = new HttpResponseMessage(status)
        { Content = new StringContent(body, System.Text.Encoding.UTF8, "application/json") };
        if (challenge) response.Headers.WwwAuthenticate.Add(new AuthenticationHeaderValue("Bearer"));
        if (retryAfter is not null) response.Headers.TryAddWithoutValidation("Retry-After", retryAfter);
        return response;
    }
}
