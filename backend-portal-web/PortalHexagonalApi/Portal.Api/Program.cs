using System.Text.Json;
using System.Text.Json.Serialization;

using Microsoft.Extensions.DependencyInjection.Extensions;

using Portal.Api.Configuration;
using Portal.Api.Authentication;
using Portal.Api.LabResults;
using Portal.Api.Nameplate;
using Portal.Api.Pipeline;
using Portal.Api.PortalStore;
using Portal.Api.ReportDocuments;
using Portal.Api.ReportProjection;
using Portal.Api.Reports;
using SpringReports = Portal.Application.Reports;
using Portal.Application.Authentication;
using Portal.Application.LabResults;
using Portal.Application.Quotes;
using Portal.Application.ReportDocuments;

WebApplicationBuilder builder = WebApplication.CreateBuilder(args);

const string PortalWebCorsPolicy = "PortalWeb";

string[] portalWebOrigins = builder.Configuration.GetSection("Cors:AllowedOrigins").Get<string[]>() ?? [];

builder.Services.AddCors(options =>
{
    options.AddPolicy(PortalWebCorsPolicy, policy =>
        policy
            .WithOrigins(portalWebOrigins)
            .AllowAnyHeader()
            .AllowAnyMethod()
            .WithExposedHeaders(["Retry-After", "Content-Disposition", .. PageQuery.ExposedHeaders]));
});

// O camelCase do portal era um padrao herdado do hospedeiro, e nao um contrato declarado:
// bastava alguem serializar de fora para o corpo virar PascalCase, e a tela nao daria erro,
// ela diria que nao existe nenhum dado de ensaio. Enum como numero falha do mesmo jeito
// silencioso, porque o rotulo da classificacao e a cor desaparecem sem mensagem. Ignorar
// nulo tambem apaga o campo que a tela usa para distinguir a visao geral da analise
// selecionada.
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
builder.Services.TryAddSingleton(TimeProvider.System);
builder.Services.AddScoped<ILoginUseCase, LoginService>();
builder.Services.AddScoped<IForConsultingLabResults, LabResultsConsultation>();
builder.Services.AddScoped<IForDownloadingSignedReports, SignedReportDownload>();
builder.Services.AddScoped<IForArchivingEquipmentReports, EquipmentReportArchiving>();
builder.Services.AddScoped<IForConsultingReports, ReportsConsultation>();

builder.Services.AddScoped<SpringReports.IForConsultingReports, SpringReports.ReportsConsultation>();

builder.Services.AddPortalDataSource(builder.Configuration);
builder.Services.AddReportDocumentRepository(builder.Configuration);

builder.Services.AddLabTestRepository(builder.Configuration);
builder.Services.AddClientLabResultsSource(builder.Configuration);
builder.Services.AddEquipmentRegistrySource(builder.Configuration);
builder.Services.AddLaboratoryProgressSource(builder.Configuration);
builder.Services.AddReportDocumentSource(builder.Configuration);
builder.Services.AddPortalStore(builder.Configuration);
builder.Services.AddReportProjection(builder.Configuration);

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
/// A suite de contrato sobe a aplicacao de verdade com
/// <c>WebApplicationFactory&lt;Program&gt;</c> e afirma sobre o corpo HTTP, e nao sobre
/// serializacao isolada. Para isso, o tipo gerado do arquivo de nivel superior precisa ser
/// alcancavel de fora do assembly.
/// </summary>
public partial class Program
{
}
