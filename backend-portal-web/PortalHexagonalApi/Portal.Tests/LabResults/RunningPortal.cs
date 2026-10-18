using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;

namespace Portal.Tests.LabResults;

/// <summary>
/// Sobe a aplicacao de verdade para o teste de contrato poder afirmar sobre o corpo HTTP
/// que a tela recebe, e nao sobre serializacao isolada. Serializar um objeto num teste prova
/// o conversor; subir o host prova o pipeline, que e onde o camelCase de verdade e decidido.
///
/// A fonte entra por <see cref="IWebHostBuilder.UseSetting"/>, e nao por variavel de
/// ambiente do processo. Variavel de ambiente vale para o processo inteiro: ficava ligada
/// depois do teste e qualquer outro teste que quisesse afirmar que a aplicacao recusa subir
/// sem fonte configurada passava a subir, porque esta classe havia configurado por ele. A
/// configuracao do host nasce e morre com esta fabrica, e chega em
/// <c>builder.Configuration</c> antes do registro de servicos, que e quando o adaptador e
/// escolhido.
/// </summary>
public sealed class RunningPortal : WebApplicationFactory<Program>
{
    public const string SampleTenant = "EMPRESA DE ENERGIA";

    protected override void ConfigureWebHost(IWebHostBuilder builder)
    {
        builder.UseSetting("LabResults:Source", "embedded-sample");
        builder.UseSetting("LabResults:DevelopmentTenant", SampleTenant);
        builder.UseSetting("Portal:DataSource", "backend-lab");
        builder.UseSetting("BackendLab:BaseUrl", "https://backend-lab.test/");
    }
}
