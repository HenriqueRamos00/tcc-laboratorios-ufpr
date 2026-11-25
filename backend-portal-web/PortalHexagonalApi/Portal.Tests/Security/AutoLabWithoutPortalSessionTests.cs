using System.Net;
using System.Text.Json.Nodes;

using Portal.Tests.Authentication;
using Portal.Tests.AutoLab;

using Xunit;

namespace Portal.Tests.Security;

/// <summary>
/// O modo <c>backend-lab</c> nao valida o JWT do portal, entao nele nao existe claim de CNPJ
/// em que confiar. Se alguem apontar as fontes do AutoLAB nesse modo, as rotas do AutoLAB
/// respondem 401 para qualquer requisicao, com ou sem Bearer, em vez de servir a base.
/// </summary>
public sealed class AutoLabWithoutPortalSessionTests
{
    [Theory]
    [InlineData("/api/equipments")]
    [InlineData("/api/equipments/NTcxMjg5")]
    [InlineData("/api/equipments/NTcxMjg5/reports")]
    [InlineData("/api/equipments/NTcxMjg5/health")]
    [InlineData("/api/equipments/NTcxMjg5/reports/MDE0MTcvMjAyNi0wMDE/document")]
    [InlineData("/api/reports")]
    [InlineData("/api/reports/MTlkNmIwZDAtYzkwZC00YzEwLTgwYjctNzAyZmY5ZjU4Mzll/document")]
    public async Task WithoutTheValidatedTokenThereIsNoScopeAndNoData(string route)
    {
        using NeverCalledHandler backend = new();
        using AuthenticationPortal portal = new(backend);
        using HttpClient anonymous = portal.CreateClient();
        using HttpClient withAnyBearer = portal.CreateClientWithToken(
            PortalSessions.TokenOf(TestCompanies.EnergyCnpj, TestCompanies.DistributorCnpj));

        foreach (HttpClient client in new[] { anonymous, withAnyBearer })
        {
            using HttpResponseMessage response = await client.GetAsync(route);
            JsonObject problem = JsonNode.Parse(await response.Content.ReadAsStringAsync())!.AsObject();

            Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
            Assert.Equal("tenant-not-identified", (string?)problem["code"]);
        }

        Assert.Equal(0, backend.Calls);
    }

    private sealed class NeverCalledHandler : HttpMessageHandler
    {
        public int Calls { get; private set; }

        protected override Task<HttpResponseMessage> SendAsync(
            HttpRequestMessage request,
            CancellationToken cancellationToken)
        {
            Calls++;

            return Task.FromResult(new HttpResponseMessage(HttpStatusCode.ServiceUnavailable));
        }
    }
}
