using Microsoft.AspNetCore.OpenApi;
using Microsoft.OpenApi;
using Portal.Api.Configuration;

namespace Portal.Api.Authentication;

public sealed class PortalBearerDocumentTransformer(PortalOptions source) : IOpenApiDocumentTransformer
{
    public Task TransformAsync(OpenApiDocument document, OpenApiDocumentTransformerContext context,
        CancellationToken cancellationToken)
    {
        if (source.DataSource != PortalOptions.LactecSource) return Task.CompletedTask;
        document.Components ??= new OpenApiComponents();
        document.Components.SecuritySchemes = new Dictionary<string, IOpenApiSecurityScheme>
        {
            ["Bearer"] = new OpenApiSecurityScheme
            {
                Type = SecuritySchemeType.Http, Scheme = "bearer", BearerFormat = "JWT",
                Description = "JWT do portal obtido em POST /auth/login. Não use tokens administrativos ou de serviço do Backend Auth."
            }
        };
        foreach (var path in document.Paths.Where(path => path.Key != "/auth/login"))
        foreach (var operation in path.Value.Operations!.Values)
        {
            operation.Security = [new OpenApiSecurityRequirement
            {
                [new OpenApiSecuritySchemeReference("Bearer", document)] = []
            }];
            operation.Responses ??= new OpenApiResponses();
            operation.Responses.TryAdd("401", new OpenApiResponse { Description = "Sessão ausente ou expirada." });
            operation.Responses.TryAdd("403", new OpenApiResponse { Description = "Sem permissão para esta operação." });
        }
        return Task.CompletedTask;
    }
}
