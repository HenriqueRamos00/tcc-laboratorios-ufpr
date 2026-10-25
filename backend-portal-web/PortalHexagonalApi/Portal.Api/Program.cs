using System.Text.Json;
using System.Text.Json.Serialization;

using Portal.Adapters.Quotes;
using Portal.Adapters.Salesforce;
using Portal.Api.Configuration;
using Portal.Api.Authentication;
using Portal.Api.LabResults;
using Portal.Application.Authentication;
using Portal.Application.LabResults;
using Portal.Application.Quotes;

WebApplicationBuilder builder = WebApplication.CreateBuilder(args);

const string PortalWebCorsPolicy = "PortalWeb";

builder.Services.AddCors(options =>
{
    options.AddPolicy(PortalWebCorsPolicy, policy =>
        policy
            .WithOrigins(
                "http://localhost:4200",
                "http://localhost",
                "http://localhost:80")
            .AllowAnyHeader()
            .AllowAnyMethod()
            .WithExposedHeaders("Retry-After"));
});

// The portal's camelCase used to be a default inherited from the host, not a declared
// contract: it was enough for someone to serialize from outside for the body to turn
// PascalCase, and the screen would not error, it would say there is no test data at all.
// An enum as a number fails the same silent way, because the classification label and
// color disappear without a message. Ignoring nulls also erases the field the screen uses
// to tell an overview from a selected analysis.
builder.Services
    .AddControllers()
    .AddJsonOptions(options =>
    {
        options.JsonSerializerOptions.PropertyNamingPolicy = JsonNamingPolicy.CamelCase;
        options.JsonSerializerOptions.DictionaryKeyPolicy = null;
        options.JsonSerializerOptions.DefaultIgnoreCondition = JsonIgnoreCondition.Never;
        options.JsonSerializerOptions.Converters.Add(
            new JsonStringEnumConverter(JsonNamingPolicy.KebabCaseLower));
    });

builder.Services.AddScoped<IQuoteUseCase, QuoteService>();
builder.Services.AddScoped<ILoginUseCase, LoginService>();
builder.Services.AddScoped<IForConsultingLabResults, LabResultsConsultation>();

builder.Services.AddPortalDataSource(builder.Configuration);

// A proposta escrita a mao continua valendo; o orcamento sem arquivo recebe a pagina
// gerada a partir dele mesmo, para o botao de baixar nunca cair no vazio na demonstracao.
builder.Services.AddScoped<IQuoteDocumentRepository>(services =>
    new GeneratedQuoteDocumentRepository(
        services.GetRequiredService<IQuoteRepository>(),
        new LocalQuoteDocumentRepository()));
builder.Services.AddLabTestRepository(builder.Configuration);
builder.Services.AddSingleton(
    builder.Configuration.GetSection(SalesforceOptions.SectionName).Get<SalesforceOptions>() ?? new SalesforceOptions());
builder.Services.AddHttpClient<ISalesforceTokenClient, SalesforceTokenClient>();
builder.Services.AddSingleton<ISalesforceTokenProvider, SalesforceTokenProvider>();

builder.Services.AddEndpointsApiExplorer();
builder.Services.AddOpenApi(options => options.AddDocumentTransformer<PortalBearerDocumentTransformer>());

WebApplication app = builder.Build();

app.MapOpenApi().AllowAnonymous();

app.UseCors(PortalWebCorsPolicy);

if (builder.Configuration["Portal:DataSource"] == PortalOptions.LactecSource)
{
    app.UseAuthentication();
    app.UseAuthorization();
}

app.MapControllers();

app.Run();

/// <summary>
/// The contract suite boots the real application with
/// <c>WebApplicationFactory&lt;Program&gt;</c> and asserts on the HTTP body, not on
/// isolated serialization. For that, the generated type of the top level file has to be
/// reachable from outside the assembly.
/// </summary>
public partial class Program
{
}
