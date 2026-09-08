using Portal.Adapters.LabResults;
using Portal.Adapters.Quotes;
using Portal.Adapters.Salesforce;
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
            .AllowAnyMethod());
});

builder.Services.AddControllers();

builder.Services.AddScoped<IQuoteUseCase, QuoteService>();
builder.Services.AddScoped<ILabResultUseCase, LabResultService>();

builder.Services.AddHttpClient<IQuoteRepository, SalesforceQuoteRepository>();
builder.Services.AddScoped<ILabResultRepository, FakeSqlServerLabResultRepository>();
builder.Services.AddSingleton(
    builder.Configuration.GetSection(SalesforceOptions.SectionName).Get<SalesforceOptions>() ?? new SalesforceOptions());
builder.Services.AddHttpClient<ISalesforceTokenClient, SalesforceTokenClient>();
builder.Services.AddSingleton<ISalesforceTokenProvider, SalesforceTokenProvider>();

builder.Services.AddEndpointsApiExplorer();
builder.Services.AddOpenApi();

WebApplication app = builder.Build();

app.MapOpenApi();

app.UseCors(PortalWebCorsPolicy);

app.MapControllers();

app.Run();
