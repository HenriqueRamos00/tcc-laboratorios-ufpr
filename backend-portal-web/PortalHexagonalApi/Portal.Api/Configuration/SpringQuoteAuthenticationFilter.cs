using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Mvc.Controllers;
using Microsoft.AspNetCore.Mvc.Filters;

using Portal.Api;
using Portal.Api.Controllers;
using Portal.Api.ReportDocuments;

namespace Portal.Api.Configuration;

/// <summary>Exige um Bearer antes das consultas de cliente que seguem para o Spring.</summary>
public sealed class SpringQuoteAuthenticationFilter(IConfiguration configuration) : IAsyncActionFilter
{
    public async Task OnActionExecutionAsync(ActionExecutingContext context, ActionExecutionDelegate next)
    {
        if (context.ActionDescriptor is ControllerActionDescriptor action
            && action.ControllerTypeInfo.AsType() is Type controllerType
            && (controllerType == typeof(QuotesController)
                || (controllerType == typeof(ReportsController)
                    && configuration["ReportDocuments:Source"] != ReportDocumentsOptions.SqlServerSource))
            && !SpringBearerCredential.IsValid(context.HttpContext.Request.Headers["Authorization"].ToString()))
        {
            context.HttpContext.Response.Headers["WWW-Authenticate"] = "Bearer";
            ControllerBase controller = (ControllerBase)context.Controller;
            context.Result = controllerType == typeof(ReportsController)
                ? controller.ReportAuthenticationRequired()
                : controller.QuoteAuthenticationRequired();
            return;
        }

        await next();
    }
}
