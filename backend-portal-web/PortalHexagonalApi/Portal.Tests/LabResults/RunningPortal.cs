using System.Net.Http.Headers;

using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;

using Portal.Tests.Authentication;
using Portal.Tests.AutoLab;

namespace Portal.Tests.LabResults;

/// <summary>
/// O portal no modo Backend Auth sobre a massa do AutoLAB. Todo cliente HTTP criado aqui ja
/// leva a sessao da empresa da amostra; quem quer ser outra empresa, ou ninguem, troca o
/// cabecalho com <see cref="PortalSessions"/>.
/// </summary>
public sealed class RunningPortal : WebApplicationFactory<Program>
{
    public const string SampleTenant = TestCompanies.Energy;

    private readonly AutoLabTestDatabase _database = AutoLabTestDatabase.WithSample();

    protected override void ConfigureWebHost(IWebHostBuilder builder)
    {
        builder.UseSampleAutoLab(_database);
        builder.UseLactecSessions();
    }

    protected override void ConfigureClient(HttpClient client)
    {
        base.ConfigureClient(client);

        client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue(
            "Bearer", PortalSessions.TokenOf(TestCompanies.EnergyCnpj));
    }

    protected override void Dispose(bool disposing)
    {
        base.Dispose(disposing);

        if (disposing) _database.Dispose();
    }
}
